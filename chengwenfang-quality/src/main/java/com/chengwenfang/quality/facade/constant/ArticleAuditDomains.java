package com.chengwenfang.quality.facade.constant;

/**
 * 审核模式、标准版本与视图兜底常量（原 domain 列已删除，用 standard_version 区分课程/热点）。
 */
public final class ArticleAuditDomains {
  /** 课程体系（逻辑域，非表列）。 */
  public static final String COURSE = "course";
  /** 热点资讯（逻辑域，非表列）。 */
  public static final String HOT_NEWS = "hot_news";
  /** 课程确定性审核模式。 */
  public static final String MODE_DETERMINISTIC = "deterministic";
  /** 热点全量审核模式。 */
  public static final String MODE_FULL = "full";
  /** 热点修复审核模式。 */
  public static final String MODE_REPAIR = "repair";
  /** 课程审核标准版本。 */
  public static final String STANDARD_COURSE_V1 = "course-audit-v1";
  /** 热点审核标准版本。 */
  public static final String STANDARD_HOT_NEWS_V1 = "hot-news-review-v1";
  /** 引擎兜底占位文章 id。 */
  public static final String ARTICLE_UNKNOWN = "article-unknown";
  /** 汇总视图：尚未审核。 */
  public static final String STATUS_NONE = "none";
  /** 汇总视图：AI 审核跳过。 */
  public static final String AI_STATUS_SKIPPED = "skipped";
  /** 规则引擎类型兜底。 */
  public static final String ENGINE_RULE = "rule";
  /** 条目严重级别：阻断。 */
  public static final String SEVERITY_BLOCKING = "blocking";
  /** 条目严重级别：提示。 */
  public static final String SEVERITY_INFO = "info";

  private ArticleAuditDomains() {}
}
