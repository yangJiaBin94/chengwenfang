package com.chengwenfang.knowledge.facade.dto.request;

import com.chengwenfang.knowledge.facade.enums.KnowledgeMediaKind;
import com.chengwenfang.knowledge.facade.enums.KnowledgeSourceKind;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 知识库对话提问。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChatMessageRequest {
  /** 用户问题正文。 */
  private String content;
  /** 限制检索的来源种类；可空表示全库。 */
  private List<KnowledgeSourceKind> sourceKinds;
  /** 限制检索的介质种类；可空表示不限。 */
  private List<KnowledgeMediaKind> mediaKinds;
}
