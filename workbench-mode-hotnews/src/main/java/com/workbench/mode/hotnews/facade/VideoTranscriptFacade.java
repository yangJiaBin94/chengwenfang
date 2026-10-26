package com.workbench.mode.hotnews.facade;

import com.workbench.sourcing.facade.enums.VideoParseStep;
import java.util.function.BiConsumer;

/**
 * 视频转录/探测门面：供雷达采集与调度任务调用，不暴露 application 实现。
 */
public interface VideoTranscriptFacade {
  /**
   * yt-dlp 探测时长（秒）；失败返回 null；同 URL 进程内缓存。
   *
   * @param url 视频 URL
   * @return 秒数；探测失败为 null
   */
  Double probeDurationSeconds(String url);

  /**
   * 超过配置上限时返回弃用文案；否则 null。
   *
   * @param url 视频 URL
   * @return 拒绝原因；可进入配额则为 null
   */
  String durationLimitRejectReason(String url);

  /**
   * 对可写目标跑字幕/ASR/画面流水线。
   *
   * @param sink         可写目标
   * @param fromStep     起始步骤
   * @param progressHook 进度回调 (step, message)；可为 null
   */
  void parseSink(
      VideoParseSink sink,
      VideoParseStep fromStep,
      BiConsumer<String, String> progressHook);

  /**
   * 清理超期视频工作目录。
   *
   * @return 删除的子目录数
   */
  int cleanupExpiredWorkDirs();
}
