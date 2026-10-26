package com.chengwenfang.quality.facade.constant;

/**
 * 热点修复候选 {@code repair_candidates.status} 取值。
 */
public final class HotArticleRepairStatuses {
  /** 待裁决。 */
  public static final String PENDING = "pending";
  /** 已采纳。 */
  public static final String ACCEPTED = "accepted";
  /** 已拒绝。 */
  public static final String REJECTED = "rejected";

  private HotArticleRepairStatuses() {}
}
