package com.workbench.crawler.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 单条搜索命中。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchHitView {
  /** 标题。 */
  private String title;
  /** URL。 */
  private String url;
  /** 摘要片段。 */
  private String snippet;
}
