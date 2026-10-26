package com.workbench.crawler.infrastructure.fetch;

import cn.hutool.core.net.url.UrlBuilder;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.workbench.crawler.application.CrawlerUrlGuard;
import com.workbench.crawler.domain.ChannelFetchContext;
import com.workbench.crawler.domain.ChannelFetchStrategy;
import com.workbench.crawler.facade.constant.ChannelFetchDefaults;
import com.workbench.crawler.facade.constant.FetchErrorCodes;
import com.workbench.crawler.facade.constant.UpstreamJsonFields;
import com.workbench.crawler.facade.dto.request.ChannelFetchConfig;
import com.workbench.crawler.facade.dto.view.FetchResult;
import com.workbench.crawler.facade.dto.view.FetchedItemView;
import com.workbench.crawler.facade.enums.ChannelType;
import com.workbench.crawler.facade.enums.GithubDateField;
import com.workbench.crawler.facade.enums.GithubSort;
import com.workbench.crawler.infrastructure.fetch.upstream.GithubSearchResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * GitHub 搜索渠道策略：按更新时间口径拼装 Search API 并解析仓库列表。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GithubChannelFetchStrategy implements ChannelFetchStrategy {
  /** URL 校验与限流。 */
  private final CrawlerUrlGuard urlGuard;

  @Override
  public ChannelType supports() {
    return ChannelType.GITHUB;
  }

  /**
   * @param context 抓取上下文
   * @return 仓库条目列表
   */
  @Override
  public FetchResult fetch(ChannelFetchContext context) {
    String requestUrl = buildSearchUrl(context);
    try {
      urlGuard.validateUrl(requestUrl);
      urlGuard.limit(requestUrl);
      try (HttpResponse response = HttpRequest.get(requestUrl)
          .header("User-Agent", context.resolveUserAgent())
          .header("Accept", ChannelFetchDefaults.GITHUB_ACCEPT)
          .timeout(FetchStrategySupport.timeoutMillis(context))
          .execute()) {
        if (!response.isOk()) {
          log.warn("GitHub 抓取 HTTP 失败 url={} status={}", requestUrl, response.getStatus());
          return new FetchResult(
              false,
              FetchErrorCodes.httpStatus(response.getStatus()),
              FetchStrategySupport.httpErrorMessage(response.getStatus(), response.body()),
              List.of());
        }
        List<FetchedItemView> items = parseGithub(response.body(), requestUrl, context.getHours());
        log.info("GitHub 抓取成功 url={} 条目数={}", requestUrl, items.size());
        return new FetchResult(true, null, null, items);
      }
    } catch (Exception ex) {
      log.warn("GitHub 抓取异常 url={} err={}", requestUrl, ex.getMessage());
      return new FetchResult(false, ex.getClass().getSimpleName(), ex.getMessage(), List.of());
    }
  }

  /**
   * 根据 baseUrl 与 config 拼装搜索 URL；时间口径固定为 updated。
   *
   * @param context 上下文
   * @return 完整 Search API URL
   */
  private String buildSearchUrl(ChannelFetchContext context) {
    String base = context.getBaseUrl() == null || context.getBaseUrl().isBlank()
        ? ChannelFetchDefaults.GITHUB_SEARCH_API_URL
        : context.getBaseUrl().trim();
    ChannelFetchConfig config = context.getConfig() == null ? new ChannelFetchConfig() : context.getConfig();
    String q = config.getQuery();
    if (q == null || q.isBlank()) {
      List<String> queries = config.resolveQueries();
      q = queries.isEmpty() ? null : queries.get(0);
    }
    if (q == null || q.isBlank()) {
      return base;
    }
    // 固定按更新时间口径过滤
    String dateQualifier = buildUpdatedQualifier(context.getHours());
    String searchQuery = StrUtil.isBlank(dateQualifier) ? q.trim() : q.trim() + " " + dateQualifier;
    String sort = GithubSort.fromOrDefault(config.getSort()).getValue();
    return UrlBuilder.ofHttp(base)
        .addQuery(UpstreamJsonFields.Q, searchQuery)
        .addQuery(UpstreamJsonFields.SORT, sort)
        .addQuery(UpstreamJsonFields.ORDER, UpstreamJsonFields.ORDER_DESC)
        .build();
  }

  /**
   * 构造 {@code updated:>yyyy-MM-dd} 查询限定。
   *
   * @param hours 时效窗口小时数
   * @return 限定串；hours 无效时为空
   */
  private static String buildUpdatedQualifier(Integer hours) {
    if (hours == null || hours <= 0) {
      return "";
    }
    LocalDate cutoff = LocalDateTime.now(ZoneOffset.UTC).minusHours(hours).toLocalDate();
    return GithubDateField.UPDATED.getValue() + ":>" + cutoff;
  }

  /**
   * 解析 GitHub Search JSON；条目时间与过滤均用 updated_at。
   *
   * @param body    响应体
   * @param baseUrl 请求 URL
   * @param hours   时效
   * @return 条目列表
   */
  private List<FetchedItemView> parseGithub(String body, String baseUrl, Integer hours) {
    GithubSearchResponse response = JSONUtil.toBean(body, GithubSearchResponse.class);
    List<FetchedItemView> out = new ArrayList<>();
    if (response == null || response.getItems() == null) {
      return out;
    }
    for (GithubSearchResponse.GithubRepoItem item : response.getItems()) {
      if (item == null) {
        continue;
      }
      LocalDateTime published = parseIsoDateTime(item.getUpdatedAt());
      if (published == null) {
        published = parseIsoDateTime(item.getCreatedAt());
      }
      if (FetchStrategySupport.isStale(published, hours)) {
        continue;
      }
      String title = item.getFullName();
      if (title == null || title.isBlank()) {
        title = item.getName() == null || item.getName().isBlank()
            ? UpstreamJsonFields.DEFAULT_REPO_NAME
            : item.getName();
      }
      String url = item.getHtmlUrl() == null || item.getHtmlUrl().isBlank() ? baseUrl : item.getHtmlUrl();
      out.add(FetchStrategySupport.item(title, url, item.getDescription(), null, published));
    }
    return out;
  }

  /**
   * 解析 GitHub ISO-8601 时间。
   *
   * @param raw 原始字符串
   * @return 本地时间；无法解析时 null
   */
  private static LocalDateTime parseIsoDateTime(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return OffsetDateTime.parse(raw).toLocalDateTime();
    } catch (Exception ignored) {
      return null;
    }
  }
}
