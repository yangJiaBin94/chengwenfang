package com.chengwenfang.mode.hotnews.application.generation.radarcollect.model;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 自适应搜索规划模型结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdaptiveSearchPlanAiResult {
  /** 本轮目标说明。 */
  private String goal;
  /** 搜索词列表。 */
  @Builder.Default
  private List<String> queries = new ArrayList<>();
}
