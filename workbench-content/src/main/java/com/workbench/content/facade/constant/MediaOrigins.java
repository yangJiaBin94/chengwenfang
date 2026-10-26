package com.workbench.content.facade.constant;

/**
 * 媒体资产来源（与 {@code media_assets.origin} 约束一致）。
 */
public final class MediaOrigins {
  private MediaOrigins() {}

  /** AI 生成（原 generated_images）。 */
  public static final String GENERATED = "generated";
  /** 学习内容同步（原 course_media）。 */
  public static final String SYNCED = "synced";
}
