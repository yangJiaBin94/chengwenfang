package com.chengwenfang.knowledge.facade.dto;

import com.chengwenfang.knowledge.facade.dto.view.KnowledgeCitationView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeToolStepView;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 助手消息 {@code payload_json} 结构化载荷。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeAnswerPayload {
  /** 引用列表。 */
  private List<KnowledgeCitationView> citations;
  /** 是否拒答。 */
  private Boolean abstained;
  /** 检索模式。 */
  private String mode;
  /** Agent 轮次。 */
  private Integer rounds;
  /** 改写后查询。 */
  private String rewrittenQuery;
  /** 降级原因。 */
  private List<String> degradedReasons;
  /** 拒答原因码。 */
  private String abstainReason;
  /** 工具步骤。 */
  private List<KnowledgeToolStepView> toolSteps;
}
