package com.workbench.crawler.infrastructure.fetch;

import cn.hutool.core.net.url.UrlBuilder;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.workbench.common.SearchQueryNormalizer;
import com.workbench.crawler.application.CrawlerUrlGuard;
import com.workbench.crawler.domain.ChannelFetchContext;
import com.workbench.crawler.domain.ChannelFetchStrategy;
import com.workbench.crawler.facade.MarketingPageFilter;
import com.workbench.crawler.facade.constant.ChannelFetchDefaults;
import com.workbench.crawler.facade.constant.FetchErrorCodes;
import com.workbench.crawler.facade.constant.UpstreamJsonFields;
import com.workbench.crawler.facade.dto.request.ChannelFetchConfig;
import com.workbench.crawler.facade.dto.view.FetchResult;
import com.workbench.crawler.facade.dto.view.FetchedItemView;
import com.workbench.crawler.facade.enums.ChannelType;
import com.workbench.crawler.infrastructure.fetch.upstream.SearxngSearchResponse;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * SearXNG 渠道策略：对多查询词限并发 GET JSON 搜索，合并去重后统一出站。
 */
@Slf4j
@Component
public class SearxngChannelFetchStrategy implements ChannelFetchStrategy {
  /** URL 校验与限流。 */
  private final CrawlerUrlGuard urlGuard;
  /** 全局渠道采集线程池。 */
  private final Executor channelFetchExecutor;

  /**
   * @param urlGuard             URL 守卫
   * @param channelFetchExecutor 全局采集线程池
   */
  public SearxngChannelFetchStrategy(
      CrawlerUrlGuard urlGuard,
      @Qualifier("channelFetchExecutor") Executor channelFetchExecutor) {
    this.urlGuard = urlGuard;
    this.channelFetchExecutor = channelFetchExecutor;
  }

  @Override
  public ChannelType supports() {
    return ChannelType.SEARXNG;
  }

  /**
   * @param context 需提供 SearXNG 实例根地址与查询词
   * @return 合并后的搜索结果
   */
  @Override
  public FetchResult fetch(ChannelFetchContext context) {
    String base = context.getBaseUrl();
    if (base == null || base.isBlank()) {
      log.warn("SearXNG 未配置实例地址");
      return new FetchResult(false, FetchErrorCodes.SEARXNG_NOT_CONFIGURED, "请填写 SearXNG 实例地址", List.of());
    }
    ChannelFetchConfig config = context.getConfig() == null ? new ChannelFetchConfig() : context.getConfig();
    // resolveQueries 已做短词规范化；此处再截断采集并发上限
    List<String> queries = sanitizeQueries(config.resolveQueries());
    if (queries.isEmpty()) {
      log.warn("SearXNG 未配置有效查询词 base={}", base);
      return new FetchResult(false, FetchErrorCodes.SEARXNG_QUERY_EMPTY, "请至少配置一条查询词", List.of());
    }
    int maxResults = config.getMaxResults() == null || config.getMaxResults() <= 0
        ? ChannelFetchDefaults.SEARXNG_DEFAULT_MAX_RESULTS
        : config.getMaxResults();
    String root = base.endsWith("/") ? base.substring(0, base.length() - 1) : base.trim();

    try {
      urlGuard.validateUrl(root);
    } catch (Exception ex) {
      log.warn("SearXNG 地址校验失败 base={} err={}", root, ex.getMessage());
      return new FetchResult(false, ex.getClass().getSimpleName(), ex.getMessage(), List.of());
    }

    // 本地实例通常只有 1～2 个 worker；限并发避免排队后全部读超时
    Semaphore gate = new Semaphore(ChannelFetchDefaults.SEARXNG_MAX_CONCURRENCY);
    log.info(
        "SearXNG 开始查询 base={} 查询数={} 并发上限={}",
        root,
        queries.size(),
        ChannelFetchDefaults.SEARXNG_MAX_CONCURRENCY);
    List<CompletableFuture<List<FetchedItemView>>> futures = new ArrayList<>();
    for (String query : queries) {
      futures.add(CompletableFuture.supplyAsync(
          () -> searchOne(root, query, maxResults, context, gate),
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
        log.warn("SearXNG 单路查询失败 base={} err={}", root, ex.getMessage());
      }
    }
    List<FetchedItemView> items = merger.toList();
    log.info("SearXNG 汇总完成 base={} 去重后条目数={}", root, items.size());
    if (items.isEmpty()) {
      return new FetchResult(
          false,
          FetchErrorCodes.SEARXNG_EMPTY,
          "SearXNG 未返回可用结果（引擎超时/验证码/被过滤）。请同时启用 RSS、GitHub 等来源，或检查实例网络与引擎配置。",
          List.of());
    }
    return new FetchResult(true, null, null, items);
  }

  /**
   * 采集侧再截断条数（规范化已在 {@link ChannelFetchConfig#resolveQueries()} 完成）。
   *
   * @param raw 已规范化查询词
   * @return 用于本次请求的查询词
   */
  static List<String> sanitizeQueries(List<String> raw) {
    List<String> normalized = SearchQueryNormalizer.normalize(raw);
    if (normalized.size() <= ChannelFetchDefaults.SEARXNG_MAX_QUERIES_PER_FETCH) {
      return normalized;
    }
    return new ArrayList<>(
        normalized.subList(0, ChannelFetchDefaults.SEARXNG_MAX_QUERIES_PER_FETCH));
  }

  /**
   * 执行单条 SearXNG 查询。
   *
   * @param root       实例根
   * @param query      查询词
   * @param maxResults 条数上限
   * @param context    上下文
   * @param gate       渠道内并发闸门
   * @return 本查询条目
   */
  private List<FetchedItemView> searchOne(
      String root,
      String query,
      int maxResults,
      ChannelFetchContext context,
      Semaphore gate) {
    boolean acquired = false;
    try {
      gate.acquire();
      acquired = true;
      UrlBuilder builder = UrlBuilder.ofHttp(root + "/search")
          .addQuery(UpstreamJsonFields.Q, query)
          .addQuery(UpstreamJsonFields.FORMAT, UpstreamJsonFields.FORMAT_JSON)
          .addQuery(UpstreamJsonFields.CATEGORIES, UpstreamJsonFields.CATEGORIES_NEWS_GENERAL)
          .addQuery(UpstreamJsonFields.TIME_RANGE, resolveTimeRange(context.getHours()));
      String searchUrl = builder.build();
      // 令牌不足时短暂重试，避免瞬时并发直接丢弃查询
      for (int attempt = 1; attempt <= 3; attempt++) {
        try {
          urlGuard.limit(searchUrl);
          break;
        } catch (IllegalStateException rateLimited) {
          if (attempt >= 3) {
            throw rateLimited;
          }
          Thread.sleep(220L * attempt);
        }
      }
      try (HttpResponse response = HttpRequest.get(searchUrl)
          .header("User-Agent", context.resolveUserAgent())
          .timeout(FetchStrategySupport.timeoutMillis(context))
          .execute()) {
        if (!response.isOk()) {
          log.warn("SearXNG HTTP 失败 query={} status={}", query, response.getStatus());
          return List.of();
        }
        return parseResults(response.body(), maxResults, query);
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      log.warn("SearXNG 查询被中断 query={}", query);
      return List.of();
    } catch (Exception ex) {
      log.warn("SearXNG 查询异常 query={} err={}", query, ex.getMessage());
      return List.of();
    } finally {
      if (acquired) {
        gate.release();
      }
    }
  }

  /**
   * 按采集时间窗映射 SearXNG time_range：≤36h→day，≤10d→week，否则 month。
   * <p>{@code hours == null/≤0} 表示不强制时效（资料补充场景），用 month 以扩大召回。
   */
  static String resolveTimeRange(Integer hours) {
    if (hours == null || hours <= 0) {
      return UpstreamJsonFields.TIME_RANGE_MONTH;
    }
    int h = hours;
    if (h <= 36) {
      return UpstreamJsonFields.TIME_RANGE_DAY;
    }
    if (h <= 240) {
      return UpstreamJsonFields.TIME_RANGE_WEEK;
    }
    return UpstreamJsonFields.TIME_RANGE_MONTH;
  }

  /**
   * 解析 SearXNG JSON results；过滤厂商产品首页等非资讯落地页。
   *
   * @param body       响应体
   * @param maxResults 上限
   * @param searchQuery 本路查询词（归因）
   * @return 条目
   */
  private List<FetchedItemView> parseResults(String body, int maxResults, String searchQuery) {
    SearxngSearchResponse response = JSONUtil.toBean(body, SearxngSearchResponse.class);
    List<FetchedItemView> out = new ArrayList<>();
    if (response == null || response.getResults() == null) {
      return out;
    }
    for (SearxngSearchResponse.SearxngResultItem item : response.getResults()) {
      if (out.size() >= maxResults) {
        break;
      }
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
          parsePublished(item.getPublishedDate(), item.getPubdate()), url);
      FetchedItemView view = FetchStrategySupport.item(title, url, item.getContent(), null, published);
      view.setSearchQuery(searchQuery);
      view.setEngines(resolveEngines(item));
      out.add(view);
    }
    return out;
  }

  /**
   * 合并 engines 列表与单引擎字段。
   *
   * @param item 上游结果行
   * @return 引擎名列表
   */
  private static List<String> resolveEngines(SearxngSearchResponse.SearxngResultItem item) {
    List<String> engines = new ArrayList<>();
    if (item.getEngines() != null) {
      for (String engine : item.getEngines()) {
        if (engine != null && !engine.isBlank()) {
          engines.add(engine.trim());
        }
      }
    }
    if (engines.isEmpty() && item.getEngine() != null && !item.getEngine().isBlank()) {
      engines.add(item.getEngine().trim());
    }
    return engines;
  }

  /**
   * 尝试解析发布时间。
   *
   * @param publishedDate 主字段
   * @param pubdate       备选字段
   * @return 本地时间或 null
   */
  private LocalDateTime parsePublished(String publishedDate, String pubdate) {
    String raw = publishedDate;
    if (raw == null || raw.isBlank()) {
      raw = pubdate;
    }
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return Instant.parse(raw).atZone(ZoneId.systemDefault()).toLocalDateTime();
    } catch (Exception ignored) {
      try {
        return LocalDateTime.parse(raw.replace(" ", "T").substring(0, Math.min(19, raw.length())));
      } catch (Exception ex) {
        return null;
      }
    }
  }
}
