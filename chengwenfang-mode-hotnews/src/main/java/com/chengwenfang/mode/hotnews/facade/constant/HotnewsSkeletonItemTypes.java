package com.chengwenfang.mode.hotnews.facade.constant;

/**
 * 文章骨架列表项类型（与 {@code hotnews_article_skeleton_items.item_type} 一致）。
 */
public final class HotnewsSkeletonItemTypes {
  private HotnewsSkeletonItemTypes() {}

  public static final String KEY_INSIGHT = "key_insight";
  public static final String IMPACT = "impact";
  public static final String READER_SIGNAL = "reader_signal";
  /** 读者点开意图（看热闹 / 核验口径 / 学方法等）。 */
  public static final String READER_INTENT = "reader_intent";
  /** 读者决策缺口（本文应补的锚点 / 读法 / 风险维度）。 */
  public static final String READER_GAP = "reader_gap";
  public static final String SECTION_PLAN = "section_plan";
  public static final String ARCHITECTURE = "architecture";
  public static final String FLOW = "flow";
  public static final String REFERENCE_HINT = "reference_hint";
}
