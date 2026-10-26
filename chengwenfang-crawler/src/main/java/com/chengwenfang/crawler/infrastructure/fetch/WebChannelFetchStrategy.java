package com.chengwenfang.crawler.infrastructure.fetch;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.chengwenfang.crawler.application.CrawlerUrlGuard;
import com.chengwenfang.crawler.domain.ChannelFetchContext;
import com.chengwenfang.crawler.domain.ChannelFetchStrategy;
import com.chengwenfang.crawler.facade.constant.ChannelFetchDefaults;
import com.chengwenfang.crawler.facade.constant.FetchErrorCodes;
import com.chengwenfang.crawler.facade.dto.request.ChannelFetchConfig;
import com.chengwenfang.crawler.facade.dto.view.FetchResult;
import com.chengwenfang.crawler.facade.dto.view.FetchedItemView;
import com.chengwenfang.crawler.facade.enums.ChannelType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

/**
 * 网页渠道抓取策略：按配置多地址顺序 HTTP GET，用 Jsoup 抽取标题与摘要后合并。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebChannelFetchStrategy implements ChannelFetchStrategy {
  /** URL 校验与限流。 */
  private final CrawlerUrlGuard urlGuard;

  @Override
  public ChannelType supports() {
    return ChannelType.WEB;
  }

  /**
   * 依次抓取配置中的全部页面地址；单地址失败不中断，全部失败才整体失败。
   *
   * @param context 抓取上下文
   * @return 合并后的网页摘要列表
   */
  @Override
  public FetchResult fetch(ChannelFetchContext context) {
    List<String> targetUrls = resolveTargetUrls(context);
    if (targetUrls.isEmpty()) {
      return new FetchResult(false, FetchErrorCodes.INVALID_CONFIG, "网页来源未配置地址", List.of());
    }
    List<FetchedItemView> items = new ArrayList<>();
    List<String> errors = new ArrayList<>();
    for (String url : targetUrls) {
      try {
        FetchedItemView item = fetchOne(context, url);
        if (item != null) {
          items.add(item);
        }
      } catch (Exception exception) {
        log.warn("网页抓取异常 url={} err={}", url, exception.getMessage());
        errors.add(url + ": " + exception.getMessage());
      }
    }
    if (items.isEmpty()) {
      String message = errors.isEmpty() ? "全部网页地址抓取失败" : String.join("; ", errors);
      return new FetchResult(false, FetchErrorCodes.ALL_FAILED, message, List.of());
    }
    log.info("网页抓取完成 urls={} ok={} fail={}", targetUrls.size(), items.size(), errors.size());
    return new FetchResult(true, null, null, items);
  }

  /**
   * 解析待抓取地址：优先 config.urls，否则回退渠道 baseUrl。
   *
   * @param context 上下文
   * @return 去重后的 URL 列表
   */
  private List<String> resolveTargetUrls(ChannelFetchContext context) {
    ChannelFetchConfig config = context.getConfig() == null ? new ChannelFetchConfig() : context.getConfig();
    Set<String> ordered = new LinkedHashSet<>();
    for (String url : config.resolveUrls()) {
      ordered.add(url);
    }
    if (ordered.isEmpty() && StrUtil.isNotBlank(context.getBaseUrl())) {
      ordered.add(context.getBaseUrl().trim());
    }
    return new ArrayList<>(ordered);
  }

  /**
   * 抓取单个页面。
   *
   * @param context 上下文
   * @param url     页面地址
   * @return 条目；HTTP 非 2xx 时抛错由调用方汇总
   * @throws Exception URL 非法、限流或 HTTP 失败时
   */
  private FetchedItemView fetchOne(ChannelFetchContext context, String url) throws Exception {
    urlGuard.validateUrl(url);
    urlGuard.limit(url);
    try (HttpResponse response = HttpRequest.get(url)
        .header("User-Agent", context.resolveUserAgent())
        .timeout(FetchStrategySupport.timeoutMillis(context))
        .execute()) {
      if (!response.isOk()) {
        log.warn("网页抓取 HTTP 失败 url={} status={}", url, response.getStatus());
        throw new IllegalStateException(
            FetchStrategySupport.httpErrorMessage(response.getStatus(), response.body()));
      }
      FetchedItemView item = parseWeb(response.body(), url);
      log.info("网页抓取成功 url={} title={}", url, item.getTitle());
      return item;
    }
  }

  /**
   * 解析 HTML 为统一条目。
   *
   * @param body HTML
   * @param url  页面地址
   * @return 单条摘要
   */
  private FetchedItemView parseWeb(String body, String url) {
    Document doc = Jsoup.parse(body, url);
    // 优先 Open Graph / Twitter Card，视频站标题常在此
    String title = firstMeta(
        doc,
        "meta[property=og:title]",
        "meta[name=twitter:title]");
    if (StrUtil.isBlank(title)) {
      title = doc.title();
    }
    if (StrUtil.isBlank(title)) {
      title = url;
    }
    String summary = firstMeta(
        doc,
        "meta[property=og:description]",
        "meta[name=twitter:description]",
        "meta[name=description]");
    if (StrUtil.isBlank(summary)) {
      summary = doc.body() == null ? "" : doc.body().text();
      if (summary.length() > ChannelFetchDefaults.WEB_SUMMARY_MAX_CHARS) {
        summary = summary.substring(0, ChannelFetchDefaults.WEB_SUMMARY_MAX_CHARS);
      }
    } else if (summary.length() > ChannelFetchDefaults.WEB_SUMMARY_MAX_CHARS) {
      summary = summary.substring(0, ChannelFetchDefaults.WEB_SUMMARY_MAX_CHARS);
    }
    return FetchStrategySupport.item(title.trim(), url, summary.trim(), null, null);
  }

  /**
   * 按选择器顺序取第一条非空 meta content。
   *
   * @param doc       文档
   * @param selectors CSS 选择器
   * @return 内容或空串
   */
  private static String firstMeta(Document doc, String... selectors) {
    for (String selector : selectors) {
      String content = doc.select(selector).attr("content");
      if (StrUtil.isNotBlank(content)) {
        return content.trim();
      }
    }
    return "";
  }
}
