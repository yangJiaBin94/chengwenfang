package com.workbench.crawler.facade.dto.request;

import com.workbench.common.SearchQueryNormalizer;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 渠道级抓取扩展配置（强类型，对应 {@code source_channels.config_json}）。
 * <p>不同 {@code ChannelType} 使用其中不同字段子集。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelFetchConfig {
  /** GitHub 单关键词；SearXNG/Tavily 也可用单条时填此字段。 */
  private String query;
  /** SearXNG / Tavily 多查询词列表。 */
  @Builder.Default
  private List<String> queries = new ArrayList<>();
  /** 单次搜索最多返回条数。 */
  private Integer maxResults;
  /** Tavily topic，如 {@code news}。 */
  private String topic;
  /** Tavily search_depth，如 {@code basic} / {@code advanced}。 */
  private String searchDepth;
  /** Tavily API Key（仅服务端使用，日志禁止打印明文）。 */
  private String apiKey;
  /** GitHub 时间口径：created / updated。 */
  private String dateField;
  /** GitHub 排序：stars / updated / forks。 */
  private String sort;
  /** 网页渠道多页面地址列表；空时回退渠道 baseUrl。 */
  @Builder.Default
  private List<String> urls = new ArrayList<>();

  /**
   * 解析有效查询词列表：优先 {@link #queries}，否则用 {@link #query}；并规范化短词。
   *
   * @return 非空查询词；无有效词时返回空列表
   */
  public List<String> resolveQueries() {
    List<String> out = new ArrayList<>();
    if (queries != null) {
      for (String item : queries) {
        if (item != null && !item.isBlank()) {
          out.add(item.trim());
        }
      }
    }
    if (out.isEmpty() && query != null && !query.isBlank()) {
      out.add(query.trim());
    }
    return SearchQueryNormalizer.normalize(out);
  }

  /**
   * 解析网页渠道待抓取地址：优先 {@link #urls}，否则空列表（调用方再回退 baseUrl）。
   *
   * @return 去空白后的 URL 列表
   */
  public List<String> resolveUrls() {
    List<String> out = new ArrayList<>();
    if (urls == null) {
      return out;
    }
    for (String item : urls) {
      if (item != null && !item.isBlank()) {
        out.add(item.trim());
      }
    }
    return out;
  }
}
