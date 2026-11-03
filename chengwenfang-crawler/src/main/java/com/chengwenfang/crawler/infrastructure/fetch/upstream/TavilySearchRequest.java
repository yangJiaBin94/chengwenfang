package com.chengwenfang.crawler.infrastructure.fetch.upstream;

import cn.hutool.core.annotation.Alias;
import java.util.List;
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
  /** 主题，如 news / finance。 */
  private String topic;
  /** 搜索深度。 */
  @Alias("search_depth")
  private String searchDepth;
  /** 最多条数。 */
  @Alias("max_results")
  private Integer maxResults;
  /** 优选域名；未设置时保持 null，避免序列化空数组。 */
  @Alias("include_domains")
  private List<String> includeDomains;
  /** 屏蔽域名；未设置时保持 null，避免序列化空数组。 */
  @Alias("exclude_domains")
  private List<String> excludeDomains;
  /** 时间窗：day / week / month / year。 */
  @Alias("time_range")
  private String timeRange;
  /** 是否返回清洗正文。 */
  @Alias("include_raw_content")
  private Boolean includeRawContent;
}
