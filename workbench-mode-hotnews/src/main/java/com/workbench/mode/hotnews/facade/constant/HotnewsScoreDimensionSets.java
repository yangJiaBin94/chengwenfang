package com.workbench.mode.hotnews.facade.constant;

/**
 * 选题评分维度集合（与 {@code hotnews_topic_score_dimensions.dimension_set} 一致）。
 */
public final class HotnewsScoreDimensionSets {
  private HotnewsScoreDimensionSets() {}

  /** 当前 AI 分维。 */
  public static final String CURRENT = "current";
  /** 评分时量表快照。 */
  public static final String RUBRIC_SNAPSHOT = "rubric_snapshot";
}
