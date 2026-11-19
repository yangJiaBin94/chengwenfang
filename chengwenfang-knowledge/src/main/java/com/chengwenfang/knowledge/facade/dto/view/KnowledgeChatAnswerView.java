package com.chengwenfang.knowledge.facade.dto.view;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 同步问答返回：助手消息与引用。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChatAnswerView {
  /** 助手消息。 */
  private KnowledgeChatMessageView message;
  /** 引用列表（与消息内 citations 一致，便于前端直取）。 */
  private List<KnowledgeCitationView> citations;
  /** 是否拒答。 */
  private Boolean abstained;
  /** 检索模式（hybrid / graph_local / graph_global / agent / abstain）。 */
  private String mode;
  /** Agent 轮次；固定管线为 0。 */
  private Integer rounds;
  /** 改写后的查询（如有）。 */
  private String rewrittenQuery;
  /** 降级原因码列表（如 vector_fallback_memory）。 */
  private List<String> degradedReasons;
  /** 拒答原因码（如 insufficient_evidence / faithfulness_failed）。 */
  private String abstainReason;
  /** 本轮工具步骤信封。 */
  private List<KnowledgeToolStepView> toolSteps;
}
