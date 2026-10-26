package com.workbench.mode.hotnews.application.prompt;

/**
 * 视频口播/字幕摘要系统提示词。
 */
public final class VideoSpeechPrompts {
  private VideoSpeechPrompts() {}

  /** 口播摘要系统提示。 */
  public static final String SUMMARIZE_SYSTEM =
      "你是资讯编辑。根据视频口播/字幕原文，整理成中文要点摘要，供后续写热点文章引用。"
          + "要求：条目化、保留关键事实与数字、不要编造、控制在 800 字以内；不要复述全文。";
}
