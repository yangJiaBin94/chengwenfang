package com.chengwenfang.knowledge.facade.dto.view;

import com.chengwenfang.knowledge.facade.enums.KnowledgeChatRole;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 知识库对话单条消息。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChatMessageView {
  /** 消息 ID。 */
  private String id;
  /** 角色。 */
  private KnowledgeChatRole role;
  /** 正文。 */
  private String content;
  /** 引用列表。 */
  private List<KnowledgeCitationView> citations;
  /** 是否拒答。 */
  private Boolean abstained;
  /** 检索模式。 */
  private String mode;
  /** Agent 轮次。 */
  private Integer rounds;
  /** 降级原因码列表。 */
  private List<String> degradedReasons;
  /** 拒答原因码。 */
  private String abstainReason;
  /** 本轮工具步骤信封。 */
  private List<KnowledgeToolStepView> toolSteps;
  /** 创建时间。 */
  private LocalDateTime createdAt;
}
