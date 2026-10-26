package com.chengwenfang.mode.hotnews.facade;

import java.time.LocalDateTime;

/**
 * 视频解析可写目标：资料池条目或雷达信源共用同一套 CLI/总结流水线。
 */
public interface VideoParseSink {
  /**
   * @return 条目主键（兼作工作目录名）
   */
  String getId();

  /**
   * @return 视频 URL
   */
  String getUrl();

  /**
   * @return 标题
   */
  String getTitle();

  /**
   * @return 已缓存的口播原文
   */
  String getVideoRawText();

  /**
   * @return speech/visual
   */
  String getVideoContentKind();

  /**
   * @param videoRawText 口播原文
   */
  void setVideoRawText(String videoRawText);

  /**
   * @param videoContentKind speech/visual
   */
  void setVideoContentKind(String videoContentKind);

  /**
   * @param videoParseStatus 解析状态
   */
  void setVideoParseStatus(String videoParseStatus);

  /**
   * @param videoParseStep 当前步骤
   */
  void setVideoParseStep(String videoParseStep);

  /**
   * @param videoParseError 失败原因
   */
  void setVideoParseError(String videoParseError);

  /**
   * @param videoParsedAt 成功时间
   */
  void setVideoParsedAt(LocalDateTime videoParsedAt);

  /**
   * @param summary AI 摘要
   */
  void setSummary(String summary);

  /**
   * 写入视频时长（秒）；信源等无此列的目标可忽略。
   *
   * @param videoDurationSeconds 时长秒数；可为 null
   */
  void setVideoDurationSeconds(Integer videoDurationSeconds);

  /** 持久化当前字段。 */
  void persist();
}
