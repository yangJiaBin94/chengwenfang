package com.chengwenfang.mode.hotnews.application.prompt;

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

  /** 逐帧描述系统提示（成文佐证选型用）。 */
  public static final String FRAME_CAPTION_SYSTEM =
      "你是资讯核对助手。将依次给出同一视频的多张关键帧。"
          + "请为每一张写一句中文画面描述，保留可读文字、数字、图表与场景要点，勿编造。"
          + "只输出 JSON 字符串数组，长度必须与图片张数相同，不要其它文字。";

  /** 成文按描述选帧系统提示。 */
  public static final String PICK_FRAME_SYSTEM =
      "你是资讯核对助手。根据「要佐证的事实」与各视频截图的文字描述，选出最能支撑该事实的一张。"
          + "只输出 JSON：{\"index\":数字}，index 为描述列表中的 0-based 下标；"
          + "若都不相关则选相对最接近的一张，不要输出其它文字。";
}
