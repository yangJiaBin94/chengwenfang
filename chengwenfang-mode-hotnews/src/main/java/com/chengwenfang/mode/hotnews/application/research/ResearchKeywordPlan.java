package com.chengwenfang.mode.hotnews.application.research;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** AI/规则生成的选题检索计划。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResearchKeywordPlan {
  /** 选题主体（如 Reflection AI Beam）。 */
  private String subject;
  /** 检索关键词列表。 */
  @Builder.Default
  private List<String> keywords = new ArrayList<>();
  /** 消歧说明，供后续相关性核验使用。 */
  private String disambiguation;
  /** 是否来自 AI（否则为规则回退）。 */
  private boolean fromAi;
}
