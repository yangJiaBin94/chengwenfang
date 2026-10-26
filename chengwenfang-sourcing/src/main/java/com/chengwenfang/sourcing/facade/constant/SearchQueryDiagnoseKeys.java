package com.chengwenfang.sourcing.facade.constant;

/**
 * 搜索词 × 网站诊断阈值与展示文案键。
 */
public final class SearchQueryDiagnoseKeys {
  private SearchQueryDiagnoseKeys() {}

  /** 桶内主题过滤丢弃数下限（含）。 */
  public static final int MIN_SKIPPED = 5;
  /** 丢弃占比下限（含）：skipped/total。 */
  public static final double MIN_SKIP_RATIO = 0.6d;
  /** 单次获取最多新增/强化拉黑组数。 */
  public static final int MAX_BLOCKS_PER_FETCH = 3;
  /** 样例标题最多条数。 */
  public static final int MAX_SAMPLE_TITLES = 5;
  /** 搜索词落库最大长度（对齐表列）。 */
  public static final int MAX_SEARCH_QUERY_LEN = 255;
  /** 站点键落库最大长度（对齐表列）。 */
  public static final int MAX_SITE_KEY_LEN = 128;
}
