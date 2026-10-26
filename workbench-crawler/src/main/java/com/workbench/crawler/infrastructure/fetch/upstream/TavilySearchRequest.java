package com.workbench.crawler.infrastructure.fetch.upstream;

import cn.hutool.core.annotation.Alias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tavily Search API 请求体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TavilySearchRequest {
  /** API Key。 */
  @Alias("api_key")
  private String apiKey;
  /** 查询词。 */
  private String query;
  /** 主题，如 news。 */
  private String topic;
  /** 搜索深度。 */
  @Alias("search_depth")
  private String searchDepth;
  /** 最多条数。 */
  @Alias("max_results")
  private Integer maxResults;
}
