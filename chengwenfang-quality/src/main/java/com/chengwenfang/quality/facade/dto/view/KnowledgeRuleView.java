package com.chengwenfang.quality.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 知识库一致性规则检查结果。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeRuleView {
  /** 规则码。 */
  private String code;
  /** 是否通过。 */
  private boolean passed;
  /** 详情。 */
  private String detail;
  /** 是否阻断。 */
  private boolean blocking;
}