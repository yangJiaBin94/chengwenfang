package com.chengwenfang.content.facade.constant;

/**
 * 热点主题写作价值量表固定维度键。
 *
 * <p>全热点主题强制包含「打开欲与可转述钩子」，用于把选题目标对齐公号传播，而非预测点击率。
 */
public final class HotNewsScoringRubricKeys {
  /** 打开欲与可转述钩子维度键。 */
  public static final String AUDIENCE_HOOK = "audience_hook";

  /** 打开欲维度中文名。 */
  public static final String AUDIENCE_HOOK_LABEL = "打开欲与可转述钩子";

  /**
   * 打开欲维度释义：编辑判断读者会否点开/转述，而非预测阅读量。
   */
  public static final String AUDIENCE_HOOK_DESCRIPTION =
      "判断是否具备冲突、反差、身份代入或一句可转发结论，让目标读者愿意点开并转述；"
          + "纯方法论文、窄基准评测、无故事钩子必须低分；有行业争议或生活化痛点可高分。";

  /** 打开欲维度目标权重（注入后须 ≥ 任一其它维）。 */
  public static final int AUDIENCE_HOOK_WEIGHT = 30;

  /** lead 推荐所需的打开欲最低分（0～10）。 */
  public static final int AUDIENCE_HOOK_LEAD_MIN_SCORE = 7;

  private HotNewsScoringRubricKeys() {}
}
