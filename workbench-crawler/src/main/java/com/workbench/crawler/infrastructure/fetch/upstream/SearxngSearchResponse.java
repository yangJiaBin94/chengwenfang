package com.workbench.crawler.infrastructure.fetch.upstream;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SearXNG {@code /search?format=json} 响应（仅解析所需字段）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearxngSearchResponse {
  /** 搜索结果。 */
  @Builder.Default
  private List<SearxngResultItem> results = new ArrayList<>();

  /** 单条结果。 */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class SearxngResultItem {
    /** 标题。 */
    private String title;
    /** 链接。 */
    private String url;
    /** 摘要/正文片段。 */
    private String content;
    /** 发布时间。 */
    private String publishedDate;
    /** 备选发布时间字段。 */
    private String pubdate;
    /** 命中引擎列表。 */
    @Builder.Default
    private List<String> engines = new ArrayList<>();
    /** 单引擎字段（部分响应只用此字段）。 */
    private String engine;
  }
}
