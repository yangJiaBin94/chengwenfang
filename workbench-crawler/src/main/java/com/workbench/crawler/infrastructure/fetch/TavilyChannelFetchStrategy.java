package com.workbench.crawler.infrastructure.fetch;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.workbench.crawler.application.CrawlerUrlGuard;
import com.workbench.crawler.domain.ChannelFetchContext;
import com.workbench.crawler.domain.ChannelFetchStrategy;
import com.workbench.crawler.facade.MarketingPageFilter;
import com.workbench.crawler.facade.constant.ChannelFetchDefaults;
import com.workbench.crawler.facade.constant.FetchErrorCodes;
import com.workbench.crawler.facade.dto.request.ChannelFetchConfig;
import com.workbench.crawler.facade.dto.view.FetchResult;
import com.workbench.crawler.facade.dto.view.FetchedItemView;
import com.workbench.crawler.facade.enums.ChannelType;
import com.workbench.crawler.facade.enums.TavilySearchDepth;
import com.workbench.crawler.facade.enums.TavilyTopic;
import com.workbench.crawler.infrastructure.fetch.upstream.TavilySearchRequest;
import com.workbench.crawler.infrastructure.fetch.upstream.TavilySearchResponse;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Tavily 渠道策略：对多查询词并行 POST 搜索 API，合并去重后统一出站。
 */
@Slf4j
@Component
public class TavilyChannelFetchStrategy implements ChannelFetchStrategy {
  /** URL 校验与限流。 */
  private final CrawlerUrlGuard urlGuard;
  /** 全局渠道采集线程池。 */
  private final Executor channelFetchExecutor;

  /**
   * @param urlGuard             URL 守卫
   * @param channelFetchExecutor 全局采集线程池
   */
  public TavilyChannelFetchStrategy(
      CrawlerUrlGuard urlGuard,
      @Qualifier("channelFetchExecutor") Executor channelFetchExecutor) {
    this.urlGuard = urlGuard;
    this.channelFetchExecutor = channelFetchExecutor;
  }

  @Override
  public ChannelType supports() {
    return ChannelType.TAVILY;
  }

  /**
   * @param context 需提供 apiKey 与查询词
   * @return 合并后的新闻搜索结果
   */
  @Override
  public FetchResult fetch(ChannelFetchContext context) {
    ChannelFetchConfig config = context.getConfig() == null ? new ChannelFetchConfig() : context.getConfig();
    String apiKey = config.getApiKey();
    if (apiKey == null || apiKey.isBlank()) {
      log.warn("Tavily 未配置 API Key");
      return new FetchResult(false, FetchErrorCodes.TAVILY_NOT_CONFIGURED, "请在来源配置中填写 Tavily API Key", List.of());
    }
    List<String> queries = config.resolveQueries();
    if (queries.isEmpty()) {
      log.warn("Tavily 未配置查询词");
      return new FetchResult(false, FetchErrorCodes.TAVILY_QUERY_EMPTY, "请至少配置一条查询词", List.of());
    }
    String apiUrl = context.getBaseUrl() == null || context.getBaseUrl().isBlank()
        ? ChannelFetchDefaults.TAVILY_SEARCH_API_URL
        : context.getBaseUrl().trim();
    int maxResults = config.getMaxResults() == null || config.getMaxResults() <= 0
        ? ChannelFetchDefaults.TAVILY_DEFAULT_MAX_RESULTS
        : config.getMaxResults();
    String topic = TavilyTopic.fromOrDefault(config.getTopic()).getValue();
    String depth = TavilySearchDepth.fromOrDefault(config.getSearchDepth()).getValue();

    try {
      urlGuard.validateUrl(apiUrl);
    } catch (Exception ex) {
      log.warn("Tavily 地址校验失败 url={} err={}", apiUrl, ex.getMessage());
      return new FetchResult(false, ex.getClass().getSimpleName(), ex.getMessage(), List.of());
    }

    log.info("Tavily 开始并行查询 查询数={} topic={}", queries.size(), topic);
    List<CompletableFuture<List<FetchedItemView>>> futures = new ArrayList<>();
    for (String query : queries) {
      futures.add(CompletableFuture.supplyAsync(
          () -> searchOne(apiUrl, apiKey, query, maxResults, topic, depth, context),
          channelFetchExecutor));
    }
    FetchedItemMerger merger = new FetchedItemMerger();
    for (CompletableFuture<List<FetchedItemView>> future : futures) {
      try {
        for (FetchedItemView item : future.join()) {
          LocalDateTime published = FetchStrategySupport.resolvePublishedAt(
              item.getPublishedAt(), item.getUrl());
          if (published != null && !published.equals(item.getPublishedAt())) {
            item.setPublishedAt(published);
          }
          if (FetchStrategySupport.isStaleOrUnknownForSearch(published, context.getHours())) {
            continue;
          }
          merger.offer(item);
        }
      } catch (Exception ex) {
        log.warn("Tavily 单路查询失败 err={}", ex.getMessage());
      }
    }
    List<FetchedItemView> items = merger.toList();
    log.info("Tavily 汇总完成 去重后条目数={}", items.size());
    return new FetchResult(true, null, null, items);
  }

  /**
   * 执行单次 Tavily 搜索（不在日志中输出 apiKey）。
   *
   * @param apiUrl     API 地址
   * @param apiKey     密钥
   * @param query      查询词
   * @param maxResults 条数
   * @param topic      主题
   * @param depth      深度
   * @param context    上下文
   * @return 本查询条目
   */
  private List<FetchedItemView> searchOne(
      String apiUrl,
      String apiKey,
      String query,
      int maxResults,
      String topic,
      String depth,
      ChannelFetchContext context) {
    TavilySearchRequest requestBody = TavilySearchRequest.builder()
        .apiKey(apiKey)
        .query(query)
        .topic(topic)
        .searchDepth(depth)
        .maxResults(maxResults)
        .build();
    try {
      urlGuard.limit(apiUrl);
      try (HttpResponse response = HttpRequest.post(apiUrl)
          .header("Content-Type", ChannelFetchDefaults.CONTENT_TYPE_JSON)
          .header("User-Agent", context.resolveUserAgent())
          .body(JSONUtil.toJsonStr(requestBody))
          .timeout(FetchStrategySupport.timeoutMillis(context))
          .execute()) {
        if (!response.isOk()) {
          log.warn("Tavily HTTP 失败 query={} status={}", query, response.getStatus());
          return List.of();
        }
        return parseResults(response.body(), query);
      }
    } catch (Exception ex) {
      log.warn("Tavily 查询异常 query={} err={}", query, ex.getMessage());
      return List.of();
    }
  }

  /**
   * 解析 Tavily results 数组。
   *
   * @param body        响应体
   * @param searchQuery 本路查询词（归因）
   * @return 条目列表
   */
  private List<FetchedItemView> parseResults(String body, String searchQuery) {
    TavilySearchResponse response = JSONUtil.toBean(body, TavilySearchResponse.class);
    List<FetchedItemView> out = new ArrayList<>();
    if (response == null || response.getResults() == null) {
      return out;
    }
    for (TavilySearchResponse.TavilyResultItem item : response.getResults()) {
      if (item == null) {
        continue;
      }
      String title = item.getTitle();
      String url = item.getUrl();
      if (title == null || title.isBlank() || url == null || url.isBlank()) {
        continue;
      }
      if (MarketingPageFilter.shouldSkip(url, title)) {
        continue;
      }
      LocalDateTime published = FetchStrategySupport.resolvePublishedAt(
          parsePublished(item.getPublishedDate()), url);
      FetchedItemView view = FetchStrategySupport.item(title, url, item.getContent(), null, published);
      view.setSearchQuery(searchQuery);
      out.add(view);
    }
    return out;
  }

  /**
   * @param raw 发布时间字符串
   * @return 本地时间或 null
   */
  private LocalDateTime parsePublished(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return Instant.parse(raw).atZone(ZoneId.systemDefault()).toLocalDateTime();
    } catch (Exception ignored) {
      try {
        String normalized = raw.replace(" ", "T");
        return LocalDateTime.parse(normalized.substring(0, Math.min(19, normalized.length())));
      } catch (Exception ex) {
        return null;
      }
    }
  }
}
