package com.chengwenfang.knowledge.facade.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 重命名知识库对话会话。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChatRenameRequest {
  /** 新标题。 */
  private String title;
}
