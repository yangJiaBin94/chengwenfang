package com.chengwenfang.mode.hotnews.facade.constant;

import java.util.List;

/** 热点选题状态词（开集，与 topic_clusters.status 对齐）。 */
public final class HotNewsTopicStatuses {
  /** 新发现（待评分/待确认）。 */
  public static final String DISCOVERED = "discovered";
  /** 待确认候选。 */
  public static final String CANDIDATE = "candidate";
  /** 已确认。 */
  public static final String CONFIRMED = "confirmed";
  /** 已选题（历史别名，语义同已确认）。 */
  public static final String SELECTED = "selected";
  /** 简报已确认（历史别名）。 */
  public static final String BRIEF_CONFIRMED = "brief_confirmed";
  /** 写作中。 */
  public static final String WRITING = "writing";
  /** 已完成。 */
  public static final String COMPLETED = "completed";
  /** 已过期。 */
  public static final String EXPIRED = "expired";

  /** 已选题页展示的状态（含写作中、已完成）。 */
  public static final List<String> CONFIRMED_LIFECYCLE =
      List.of(CONFIRMED, WRITING, COMPLETED);

  /**
   * 是否属于「已确认及之后」生命周期（含历史别名）。
   *
   * @param status 状态字符串
   * @return 匹配则为 true
   */
  public static boolean isConfirmedLifecycle(String status) {
    return CONFIRMED.equals(status)
        || SELECTED.equals(status)
        || BRIEF_CONFIRMED.equals(status)
        || WRITING.equals(status)
        || COMPLETED.equals(status);
  }

  private HotNewsTopicStatuses() {}
}
