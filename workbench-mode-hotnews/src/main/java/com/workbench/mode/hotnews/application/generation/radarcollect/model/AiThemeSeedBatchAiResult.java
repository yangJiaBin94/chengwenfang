package com.workbench.mode.hotnews.application.generation.radarcollect.model;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 主题种子素材批量 JSON：{@code {"items":[{...},...]} }。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiThemeSeedBatchAiResult {
  /** 种子条目列表。 */
  @Builder.Default
  private List<AiThemeSeedAiResult> items = new ArrayList<>();
}
