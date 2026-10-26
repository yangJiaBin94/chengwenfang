package com.chengwenfang.crawler.infrastructure.fetch.upstream;

import cn.hutool.core.annotation.Alias;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tavily Search API 响应（仅解析所需字段）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TavilySearchResponse {
  /** 搜索结果。 */
  @Builder.Default
  private List<TavilyResultItem> results = new ArrayList<>();

  /** 单条结果。 */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class TavilyResultItem {
    /** 标题。 */
    private String title;
    /** 链接。 */
    private String url;
    /** 摘要。 */
    private String content;
    /** 发布时间。 */
    @Alias("published_date")
    private String publishedDate;
  }
}
