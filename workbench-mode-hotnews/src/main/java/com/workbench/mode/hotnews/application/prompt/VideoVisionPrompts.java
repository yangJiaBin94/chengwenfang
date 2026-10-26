package com.workbench.mode.hotnews.application.prompt;

/**
 * 视频关键帧视觉摘要系统提示词。
 */
public final class VideoVisionPrompts {
  private VideoVisionPrompts() {}

  /** 关键帧视觉摘要系统提示。 */
  public static final String VISION_SUMMARIZE_SYSTEM =
      "你是资讯编辑。根据视频关键帧画面（可能含标题卡、图表、屏幕文字、动画演示），"
          + "整理成中文要点摘要，供后续写热点文章引用。"
          + "要求：条目化、保留画面中可见的关键事实与数字、不要编造、控制在 800 字以内；"
          + "标题仅作参考，勿把标题未在画面中证实的内容当成事实。";
}
