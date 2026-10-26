package com.workbench.crawler.facade.constant;

/**
 * 抓取失败错误码（开集常量，供策略与 Facade 共用）。
 */
public final class FetchErrorCodes {
  private FetchErrorCodes() {}

  /** 请求对象为空或非法。 */
  public static final String INVALID_REQUEST = "invalid_request";
  /** 未知或不支持的渠道类型。 */
  public static final String UNSUPPORTED_CHANNEL_TYPE = "unsupported_channel_type";
  /** SearXNG 实例未配置。 */
  public static final String SEARXNG_NOT_CONFIGURED = "searxng_not_configured";
  /** SearXNG 查询词为空。 */
  public static final String SEARXNG_QUERY_EMPTY = "searxng_query_empty";
  /** Tavily API Key 未配置。 */
  public static final String TAVILY_NOT_CONFIGURED = "tavily_not_configured";
  /** Tavily 查询词为空。 */
  public static final String TAVILY_QUERY_EMPTY = "tavily_query_empty";
  /** SearXNG 请求成功但无可用结果（引擎超时、验证码或全部被过滤）。 */
  public static final String SEARXNG_EMPTY = "searxng_empty";
  /** Tavily 请求成功但无可用结果。 */
  public static final String TAVILY_EMPTY = "tavily_empty";
  /** 渠道配置缺失或非法（如网页未配置地址）。 */
  public static final String INVALID_CONFIG = "invalid_config";
  /** 多地址/多查询全部失败。 */
  public static final String ALL_FAILED = "all_failed";

  /**
   * HTTP 状态错误码前缀拼装。
   *
   * @param status HTTP 状态码
   * @return 如 {@code http_403}
   */
  public static String httpStatus(int status) {
    return "http_" + status;
  }
}
