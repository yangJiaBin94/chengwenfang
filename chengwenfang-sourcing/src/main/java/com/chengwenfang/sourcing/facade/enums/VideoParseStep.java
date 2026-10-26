package com.chengwenfang.sourcing.facade.enums;

import cn.hutool.core.util.StrUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 视频解析步骤（进度展示与按步重试）。
 */
public enum VideoParseStep {
  /** 探测时长（元数据，不下载媒体）。 */
  PROBE_DURATION("probe_duration", "检查时长"),
  /** 提取字幕。 */
  FETCH_SUBTITLE("fetch_subtitle", "提取字幕"),
  /** 下载音频。 */
  DOWNLOAD_AUDIO("download_audio", "下载音频"),
  /** 转换格式。 */
  CONVERT_FORMAT("convert_format", "转换格式"),
  /** 语音识别。 */
  ASR("asr", "语音识别"),
  /** 下载视频（画面链路）。 */
  DOWNLOAD_VIDEO("download_video", "下载视频"),
  /** 提取关键帧。 */
  EXTRACT_FRAMES("extract_frames", "提取关键帧"),
  /** 多模态画面总结。 */
  VISION_SUMMARIZE("vision_summarize", "画面理解"),
  /** 文本 AI 总结要点（语音链路）。 */
  SUMMARIZE("summarize", "总结内容");

  private final String value;
  private final String label;

  VideoParseStep(String value, String label) {
    this.value = value;
    this.label = label;
  }

  /** @return 步骤 key */
  public String getValue() {
    return value;
  }

  /** @return 中文展示名 */
  public String getLabel() {
    return label;
  }

  /**
   * 解析步骤；无法识别时返回 null。
   *
   * @param raw 原始字符串
   * @return 枚举或 null
   */
  public static VideoParseStep from(String raw) {
    if (StrUtil.isBlank(raw)) {
      return null;
    }
    String key = raw.trim().toLowerCase(Locale.ROOT);
    for (VideoParseStep step : values()) {
      if (step.value.equals(key)) {
        return step;
      }
    }
    return null;
  }

  /** CLI 可执行的步骤（不含 Java 侧总结）。 */
  public boolean isCliStep() {
    return this != SUMMARIZE && this != VISION_SUMMARIZE;
  }

  /**
   * 画面链路步骤（含起止）。
   *
   * @return 下载视频 → 抽帧 → 画面总结
   */
  public static List<VideoParseStep> visualPipeline() {
    return List.of(DOWNLOAD_VIDEO, EXTRACT_FRAMES, VISION_SUMMARIZE);
  }

  /**
   * 从指定步骤起（含）到结束的完整枚举顺序流水线。
   *
   * @param from 起始步骤；null 表示从头
   * @return 有序列表
   */
  public static List<VideoParseStep> pipelineFrom(VideoParseStep from) {
    VideoParseStep start = from == null ? FETCH_SUBTITLE : from;
    List<VideoParseStep> out = new ArrayList<>();
    boolean started = false;
    for (VideoParseStep step : values()) {
      if (step == start) {
        started = true;
      }
      if (started) {
        out.add(step);
      }
    }
    if (out.isEmpty()) {
      out.add(SUMMARIZE);
    }
    return out;
  }

  /**
   * 画面链路中从指定步骤起的子序列。
   *
   * @param from 起始；null 或不在画面链路则从下载视频起
   * @return 子列表
   */
  public static List<VideoParseStep> visualPipelineFrom(VideoParseStep from) {
    List<VideoParseStep> full = visualPipeline();
    if (from == null || !full.contains(from)) {
      return full;
    }
    int startIndex = full.indexOf(from);
    return full.subList(startIndex, full.size());
  }
}
