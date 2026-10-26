package com.workbench.crawler.facade.dto.view;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 单条抓取结果条目。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FetchedItemView {
  /** 标题。 */
  private String title;
  /** 原文 URL。 */
  private String url;
  /** 摘要。 */
  private String summary;
  /** 作者。 */
  private String author;
  /** 发布时间。 */
  private LocalDateTime publishedAt;
  /** 命中本条结果的搜索词；非搜索渠道可空。 */
  private String searchQuery;
  /** 上游引擎名列表（如 SearXNG engines）；可空。 */
  @Builder.Default
  private List<String> engines = new ArrayList<>();
}
