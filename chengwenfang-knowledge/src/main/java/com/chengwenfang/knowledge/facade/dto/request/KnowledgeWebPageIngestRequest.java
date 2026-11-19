package com.chengwenfang.knowledge.facade.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 网页 URL 入库请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeWebPageIngestRequest {
  /** 网页地址。 */
  private String url;
  /** 可选标题覆盖。 */
  private String title;
}
