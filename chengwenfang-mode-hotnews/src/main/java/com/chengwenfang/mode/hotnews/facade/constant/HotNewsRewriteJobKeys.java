package com.chengwenfang.mode.hotnews.facade.constant;

/**
 * 热点文章改写 Job 出参扩展键。
 */
public final class HotNewsRewriteJobKeys {
  private HotNewsRewriteJobKeys() {}

  /** 改写指令摘要。 */
  public static final String INSTRUCTION = "instruction";
  /** 是否已改写。 */
  public static final String REWRITTEN = "rewritten";
  /** 插图复用报告。 */
  public static final String ILLUSTRATION_REUSE = "illustrationReuse";
  /** 是否仍有插图待重生。 */
  public static final String IMAGES_REGEN_PENDING = "imagesRegenPending";
  /** 配图提示文案。 */
  public static final String IMAGES_REGEN_HINT = "imagesRegenHint";
  /** 已入队的仅配图 Job id；可空。 */
  public static final String IMAGES_REGEN_JOB_ID = "imagesRegenJobId";
}
