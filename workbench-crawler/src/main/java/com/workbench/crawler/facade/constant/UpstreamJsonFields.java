package com.workbench.crawler.facade.constant;

/**
 * 上游搜索/Feed JSON 字段名（协议契约，集中管理避免散落）。
 */
public final class UpstreamJsonFields {
  private UpstreamJsonFields() {}

  /** 通用 results 数组。 */
  public static final String RESULTS = "results";
  /** 通用 title。 */
  public static final String TITLE = "title";
  /** 通用 url。 */
  public static final String URL = "url";
  /** 通用 content/摘要。 */
  public static final String CONTENT = "content";
  /** SearXNG publishedDate。 */
  public static final String PUBLISHED_DATE = "publishedDate";
  /** SearXNG 备选 pubdate。 */
  public static final String PUBDATE = "pubdate";
  /** Tavily published_date。 */
  public static final String TAVILY_PUBLISHED_DATE = "published_date";
  /** GitHub items。 */
  public static final String ITEMS = "items";
  /** GitHub created_at。 */
  public static final String CREATED_AT = "created_at";
  /** GitHub full_name。 */
  public static final String FULL_NAME = "full_name";
  /** GitHub name。 */
  public static final String NAME = "name";
  /** GitHub html_url。 */
  public static final String HTML_URL = "html_url";
  /** GitHub description。 */
  public static final String DESCRIPTION = "description";
  /** Tavily api_key。 */
  public static final String API_KEY = "api_key";
  /** Tavily/通用 query。 */
  public static final String QUERY = "query";
  /** Tavily topic。 */
  public static final String TOPIC = "topic";
  /** Tavily search_depth。 */
  public static final String SEARCH_DEPTH = "search_depth";
  /** Tavily max_results。 */
  public static final String MAX_RESULTS = "max_results";
  /** SearXNG q。 */
  public static final String Q = "q";
  /** SearXNG format。 */
  public static final String FORMAT = "format";
  /** SearXNG format=json。 */
  public static final String FORMAT_JSON = "json";
  /** SearXNG categories。 */
  public static final String CATEGORIES = "categories";
  /**
   * 热点采集默认分类。
   * <p>不用 news：国内可用引擎多在 general/videos，news 常只剩 reuters 等海外源并超时。
   */
  public static final String CATEGORIES_NEWS_GENERAL = "general,videos";
  /** SearXNG time_range。 */
  public static final String TIME_RANGE = "time_range";
  /** 近一周。 */
  public static final String TIME_RANGE_WEEK = "week";
  /** 近一天。 */
  public static final String TIME_RANGE_DAY = "day";
  /** 近一月。 */
  public static final String TIME_RANGE_MONTH = "month";
  /** GitHub sort。 */
  public static final String SORT = "sort";
  /** GitHub order。 */
  public static final String ORDER = "order";
  /** GitHub order=desc。 */
  public static final String ORDER_DESC = "desc";
  /** 缺省仓库名占位。 */
  public static final String DEFAULT_REPO_NAME = "repo";
}
