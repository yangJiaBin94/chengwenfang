package com.chengwenfang.mode.hotnews.facade.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 手动向选题资料池添加素材的请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualResearchItemRequest {
  /** 素材链接（http/https）。 */
  private String url;
}
