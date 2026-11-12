package com.chengwenfang.mode.hotnews.facade.constant;

/**
 * 热点文章 generationSettings Map / 表字段对应的键名。
 */
public final class HotNewsGenerationSettingsKeys {
  private HotNewsGenerationSettingsKeys() {}

  /** 字数档。 */
  public static final String WORD_COUNT_OPTION = "wordCountOption";
  /** 插图档。 */
  public static final String IMAGE_COUNT_OPTION = "imageCountOption";
  /** 工作标题。 */
  public static final String WORKING_TITLE = "workingTitle";
  /** 插画风格。 */
  public static final String ILLUSTRATION_STYLE = "illustrationStyle";
  /** 封面说明。 */
  public static final String COVER_BRIEF = "coverBrief";
  /** 配图提示词要求。 */
  public static final String IMAGE_PROMPT_REQUIREMENTS = "imagePromptRequirements";
  /** 最近改写指令。 */
  public static final String LAST_REWRITE_INSTRUCTION = "lastRewriteInstruction";
  /** 改写时间。 */
  public static final String REWRITTEN_AT = "rewrittenAt";
  /** 最近一次优化诊断结果（对象）。 */
  public static final String LAST_OPTIMIZE_CRITIQUE = "lastOptimizeCritique";
  /** 诊断时间（嵌在 lastOptimizeCritique 内）。 */
  public static final String CRITIQUED_AT = "critiquedAt";
}
