package com.chengwenfang.knowledge.facade.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 创建知识库对话会话。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChatCreateRequest {
  /** 会话标题；可空则由服务端默认。 */
  private String title;
}
