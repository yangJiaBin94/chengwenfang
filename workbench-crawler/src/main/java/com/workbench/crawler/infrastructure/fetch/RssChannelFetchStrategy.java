package com.workbench.crawler.infrastructure.fetch;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import com.workbench.crawler.application.CrawlerUrlGuard;
import com.workbench.crawler.domain.ChannelFetchContext;
import com.workbench.crawler.domain.ChannelFetchStrategy;
import com.workbench.crawler.facade.constant.FetchErrorCodes;
import com.workbench.crawler.facade.dto.view.FetchResult;
import com.workbench.crawler.facade.dto.view.FetchedItemView;
import com.workbench.crawler.facade.enums.ChannelType;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * RSS / Atom 渠道抓取策略：HTTP GET feed 后用 Rome 解析。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RssChannelFetchStrategy implements ChannelFetchStrategy {
  /** URL 校验与限流。 */
  private final CrawlerUrlGuard urlGuard;

  @Override
  public ChannelType supports() {
    return ChannelType.RSS;
  }

  /**
   * @param context 抓取上下文（需 baseUrl）
   * @return 统一抓取结果
   */
  @Override
  public FetchResult fetch(ChannelFetchContext context) {
    String url = context.getBaseUrl();
    try {
      urlGuard.validateUrl(url);
      urlGuard.limit(url);
      try (HttpResponse response = HttpRequest.get(url)
          .header("User-Agent", context.resolveUserAgent())
          .timeout(FetchStrategySupport.timeoutMillis(context))
          .execute()) {
        if (!response.isOk()) {
          log.warn("RSS 抓取 HTTP 失败 url={} status={}", url, response.getStatus());
          return new FetchResult(
              false,
              FetchErrorCodes.httpStatus(response.getStatus()),
              FetchStrategySupport.httpErrorMessage(response.getStatus(), response.body()),
              List.of());
        }
        List<FetchedItemView> items = parseRss(response.bodyBytes(), url, context.getHours());
        log.info("RSS 抓取成功 url={} 条目数={}", url, items.size());
        return new FetchResult(true, null, null, items);
      }
    } catch (Exception ex) {
      log.warn("RSS 抓取异常 url={} err={}", url, ex.getMessage());
      return new FetchResult(false, ex.getClass().getSimpleName(), ex.getMessage(), List.of());
    }
  }

  /**
   * 解析 RSS/Atom 并按 hours 过滤。
   *
   * @param body    响应体
   * @param baseUrl 源地址
   * @param hours   时效窗口
   * @return 条目列表
   */
  private List<FetchedItemView> parseRss(byte[] body, String baseUrl, Integer hours) throws Exception {
    SyndFeed feed = new SyndFeedInput().build(new XmlReader(new ByteArrayInputStream(body)));
    List<FetchedItemView> items = new ArrayList<>();
    for (SyndEntry entry : feed.getEntries()) {
      LocalDateTime published = FetchStrategySupport.toLocalDateTime(entry.getPublishedDate());
      if (FetchStrategySupport.isStale(published, hours)) {
        continue;
      }
      String link = entry.getLink() == null ? baseUrl : entry.getLink();
      String summary = entry.getDescription() == null ? null : entry.getDescription().getValue();
      items.add(FetchStrategySupport.item(entry.getTitle(), link, summary, null, published));
    }
    return items;
  }
}
