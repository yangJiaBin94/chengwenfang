package com.chengwenfang.ai.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 联网搜索引用（URL 真相源）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSearchCitation {
  /** 引用链接。 */
  private String url;
  /** 引用标题；可空。 */
  private String title;
}
