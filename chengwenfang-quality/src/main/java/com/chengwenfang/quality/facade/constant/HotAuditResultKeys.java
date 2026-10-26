package com.chengwenfang.quality.facade.constant;

/**
 * 热点审稿结果 Map 顶层键（原 result_json）。
 */
public final class HotAuditResultKeys {
  /** 结果状态。 */
  public static final String STATUS = "status";
  /** 是否阻塞失败。 */
  public static final String BLOCKING_FAILED = "blockingFailed";
  /** 问题明细列表。 */
  public static final String RESULTS = "results";
  /** 人工决定状态。 */
  public static final String DECISION_STATUS = "decisionStatus";
  /** 人工决定说明。 */
  public static final String DECISION_COMMENT = "decisionComment";
  /** 人工决定时间字符串。 */
  public static final String DECIDED_AT = "decidedAt";

  private HotAuditResultKeys() {}
}
