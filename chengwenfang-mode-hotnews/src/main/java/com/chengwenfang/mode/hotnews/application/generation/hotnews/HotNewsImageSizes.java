package com.chengwenfang.mode.hotnews.application.generation.hotnews;

import com.chengwenfang.mode.hotnews.facade.enums.HotNewsImageCountOption;
import com.chengwenfang.mode.hotnews.facade.enums.HotNewsWordCountOption;

/**
 * 热点配图尺寸与张数映射常量。
 */
public final class HotNewsImageSizes {
  private HotNewsImageSizes() {}

  /** 封面宽。 */
  public static final int COVER_WIDTH = 2400;
  /** 封面高。 */
  public static final int COVER_HEIGHT = 1024;
  /** 封面尺寸字符串。 */
  public static final String COVER_SIZE = "2400x1024";

  /** 正文插图宽。 */
  public static final int INLINE_WIDTH = 2048;
  /** 正文插图高。 */
  public static final int INLINE_HEIGHT = 1152;
  /** 正文插图尺寸字符串。 */
  public static final String INLINE_SIZE = "2048x1152";

  /** 默认字数档。 */
  public static final String DEFAULT_WORD_COUNT_OPTION =
      HotNewsWordCountOption.defaultOption().getValue();
  /** 默认插图档。 */
  public static final String DEFAULT_IMAGE_COUNT_OPTION =
      HotNewsImageCountOption.defaultOption().getValue();

  /** 封面 Markdown 占位。 */
  public static final String COVER_TOKEN = "[[HOT_NEWS_COVER]]";

  /**
   * 将 imageCountOption 映射为正文插图张数（不含封面；至少 2 张）。
   *
   * @param option 工作区档位，如 2-3 / 3-5 / 6-8
   * @return 正文插图数量
   */
  public static int mapInlineCount(String option) {
    return HotNewsImageCountOption.resolveInlineCount(option);
  }

  /**
   * 正文插图占位 token。
   *
   * @param sequenceNo 从 1 起的序号
   * @return 占位字符串
   */
  public static String inlineToken(int sequenceNo) {
    return "[[HOT_NEWS_IMAGE_" + sequenceNo + "]]";
  }
}
