package com.workbench.sourcing.application.model;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Python 视频解析 CLI 单步 stdout JSON。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoTranscriptCliResult {
  /** 是否成功。 */
  private boolean ok;
  /** 步骤 key。 */
  private String step;
  /** 字幕或 ASR 文本。 */
  private String text;
  /** subtitle / asr。 */
  private String source;
  /** 错误信息。 */
  private String error;
  /** 产物路径。 */
  private Map<String, Object> artifacts;
  /** 视频时长（秒）；{@code probe_duration} 步骤返回。 */
  private Double durationSeconds;
}
