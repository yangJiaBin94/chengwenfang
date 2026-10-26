package com.chengwenfang.crawler.facade.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 抓取请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FetchRequest {
  /** 目标基础地址（仅 http/https）；搜索型可为实例根或 API 地址。 */
  private String url;
  /** 解析类型：web、rss、github、searxng、tavily 等。 */
  private String type;
  /** 超时秒数，null 时实现侧用默认值。 */
  private Integer timeoutSeconds;
  /** 仅保留最近 N 小时内的条目，null 表示不过滤。 */
  private Integer hours;
  /** 自定义 UA，可空。 */
  private String userAgent;
  /** 类型扩展配置（查询词、API Key 等）。 */
  private ChannelFetchConfig config;
}
