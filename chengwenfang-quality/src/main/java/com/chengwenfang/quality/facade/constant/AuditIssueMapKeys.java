package com.chengwenfang.quality.facade.constant;

/**
 * 热点审稿单条问题 Map 键（原 result_json.results[]）。
 */
public final class AuditIssueMapKeys {
  /** 规则码。 */
  public static final String CODE = "code";
  /** 引擎。 */
  public static final String ENGINE = "engine";
  /** 分类。 */
  public static final String CATEGORY = "category";
  /** 状态。 */
  public static final String STATUS = "status";
  /** 严重度。 */
  public static final String SEVERITY = "severity";
  /** 是否阻塞。 */
  public static final String BLOCKING = "blocking";
  /** 标题。 */
  public static final String TITLE = "title";
  /** 详情。 */
  public static final String DETAIL = "detail";
  /** 定位列表。 */
  public static final String LOCATIONS = "locations";
  /** 证据列表。 */
  public static final String EVIDENCE = "evidence";
  /** 是否可修复。 */
  public static final String REPAIRABLE = "repairable";
  /** 是否需人工确认。 */
  public static final String REQUIRES_HUMAN_CONFIRMATION = "requiresHumanConfirmation";
  /** 兼容别名：规则 id。 */
  public static final String ID = "id";
  /** 兼容别名：名称。 */
  public static final String NAME = "name";
  /** 兼容别名：消息。 */
  public static final String MESSAGE = "message";

  private AuditIssueMapKeys() {}
}
