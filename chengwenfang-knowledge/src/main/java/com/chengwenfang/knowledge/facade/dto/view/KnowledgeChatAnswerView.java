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
}
