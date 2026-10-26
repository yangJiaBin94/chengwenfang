package com.chengwenfang.crawler.facade.constant;

/**
 * 渠道抓取默认值与协议常量（避免策略内散落魔法数/串）。
 */
public final class ChannelFetchDefaults {
  private ChannelFetchDefaults() {}

  /** 缺省 User-Agent。 */
  public static final String DEFAULT_USER_AGENT = "chengwenfang-crawler/0.1";
  /** 缺省超时秒数。 */
  public static final int DEFAULT_TIMEOUT_SECONDS = 20;
  /** 连通性探测超时秒数。 */
  public static final int TEST_TIMEOUT_SECONDS = 15;
  /** 连通性探测默认回溯小时。 */
  public static final int TEST_HOURS = 72;
  /** 短时补抓网页超时秒数。 */
  public static final int QUICK_WEB_TIMEOUT_SECONDS = 5;
  /** 探测成功时最多返回样本条数。 */
  public static final int TEST_SAMPLE_LIMIT = 3;
  /** 网页摘要最大字符数。 */
  public static final int WEB_SUMMARY_MAX_CHARS = 500;
  /** SearXNG 默认单次条数（每一条查询词的上限，非全部查询合计）。 */
  public static final int SEARXNG_DEFAULT_MAX_RESULTS = 25;
  /** 本地 SearXNG 默认实例根地址。 */
  public static final String SEARXNG_DEFAULT_INSTANCE_URL = "http://127.0.0.1:8080";
  /**
   * 单次采集最多提交的查询词条数。
   * <p>与 {@link com.chengwenfang.common.SearchQueryNormalizer#MAX_QUERIES} 对齐；
   * 本地 SearXNG 默认单 worker，过多并行会排队后全部 Read timed out。
   */
  public static final int SEARXNG_MAX_QUERIES_PER_FETCH =
      com.chengwenfang.common.SearchQueryNormalizer.MAX_QUERIES;
  /** 单条查询词最大字符数（与规范化器一致）。 */
  public static final int SEARXNG_MAX_QUERY_CHARS =
      com.chengwenfang.common.SearchQueryNormalizer.MAX_QUERY_CHARS;
  /** 同一渠道内对 SearXNG 的最大并发查询数。 */
  public static final int SEARXNG_MAX_CONCURRENCY = 2;
  /** Tavily 默认单次条数。 */
  public static final int TAVILY_DEFAULT_MAX_RESULTS = 8;
  /** 主机限流：每秒令牌数。 */
  public static final long HOST_RATE_PER_SECOND = 5L;
  /** GitHub Search API 默认地址。 */
  public static final String GITHUB_SEARCH_API_URL = "https://api.github.com/search/repositories";
  /** Tavily Search API 默认地址。 */
  public static final String TAVILY_SEARCH_API_URL = "https://api.tavily.com/search";
  /** HTTP 协议。 */
  public static final String SCHEME_HTTP = "http";
  /** HTTPS 协议。 */
  public static final String SCHEME_HTTPS = "https";
  /** GitHub Accept 头。 */
  public static final String GITHUB_ACCEPT = "application/vnd.github+json";
  /** JSON Content-Type。 */
  public static final String CONTENT_TYPE_JSON = "application/json";
}
