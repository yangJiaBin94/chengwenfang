package com.workbench.mode.hotnews.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 选题某一评分维度的 AI 打分结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicDimensionScore {
  /** 维度键（与主题量表 key 对齐）。 */
  private String key;
  /** 展示名快照。 */
  private String label;
  /** 权重快照（百分比）。 */
  private Integer weight;
  /** 0～10 分。 */
  private Integer score;
  /** 一句理由。 */
  private String reason;
}
