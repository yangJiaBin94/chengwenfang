package com.chengwenfang.content.facade.constant;

/**
 * 主题画像列表项类型（{@code theme_profile_items.item_type}）。
 */
public final class ThemeProfileItemTypes {
  private ThemeProfileItemTypes() {}

  /** 读者画像。 */
  public static final String READER = "reader";
  /** 先修要求。 */
  public static final String PREREQUISITE = "prerequisite";
  /** 学习成果。 */
  public static final String OUTCOME = "outcome";
  /** 必须覆盖（课程）。 */
  public static final String MUST_COVER = "must_cover";
  /** 必须盯紧（热点）。 */
  public static final String MUST_WATCH = "must_watch";
  /** 值得写（热点价值过滤）。 */
  public static final String WORTH_WRITING = "worth_writing";
  /** 检索关键词。 */
  public static final String SEARCH_QUERY = "search_query";
  /** 种子渠道。 */
  public static final String SEED_CHANNEL = "seed_channel";
  /** 不在范围内。 */
  public static final String OUT_OF_SCOPE = "out_of_scope";
}
