package com.chengwenfang.mode.hotnews.facade.dto.request;

import com.chengwenfang.mode.hotnews.domain.TopicDimensionScore;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 将 AI 写作价值评分写回选题。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicWritingScoreApplyRequest {
  /** 选题 ID。 */
  private String topicId;
  /** 写作价值总分 0～100。 */
  private Integer writingScore;
  /** 评分理由（编辑判断）。 */
  private String scoreReason;
  /** 分维结果。 */
  @Builder.Default
  private List<TopicDimensionScore> dimensions = new ArrayList<>();
  /** 量表版本快照。 */
  private Integer rubricVersion;
  /** 量表维度快照。 */
  @Builder.Default
  private List<TopicDimensionScore> rubricSnapshot = new ArrayList<>();
  /** 推荐用法。 */
  private String recommendUse;
  /** 风险提示。 */
  private String riskNote;
}
