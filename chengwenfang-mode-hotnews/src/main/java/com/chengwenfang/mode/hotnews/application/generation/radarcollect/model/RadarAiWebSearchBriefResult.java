package com.chengwenfang.mode.hotnews.application.generation.radarcollect.model;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 雷达 AI 联网选题搜索指令生成结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RadarAiWebSearchBriefResult {
  /** 可直接作为联网搜索 user prompt 的完整指令。 */
  private String searchBrief;
  /** 指令中重点强调的量表维度中文名。 */
  @Builder.Default
  private List<String> focusDimensions = new ArrayList<>();
  /** 是否来自 AI（false 表示调用方应使用硬编码回退）。 */
  private boolean fromAi;
}
