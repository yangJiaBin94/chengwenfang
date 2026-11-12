package com.chengwenfang.mode.hotnews.application.generation.hotnews.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 成文佐证可选的视频抽帧（文件 + 画面描述）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsVideoFrameEvidence {
  /** stored_files id。 */
  private String fileId;
  /** 逐帧中文描述；可空。 */
  private String caption;
}
