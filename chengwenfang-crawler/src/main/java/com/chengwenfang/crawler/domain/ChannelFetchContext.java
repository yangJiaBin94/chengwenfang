package com.chengwenfang.crawler.domain;

import com.chengwenfang.crawler.facade.constant.ChannelFetchDefaults;
import com.chengwenfang.crawler.facade.dto.request.ChannelFetchConfig;
import com.chengwenfang.crawler.facade.dto.request.FetchRequest;
import com.chengwenfang.crawler.facade.enums.ChannelType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 渠道抓取策略入参（由 {@link FetchRequest} 组装）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelFetchContext {
  /** 渠道类型。 */
  private ChannelType type;
  /** 基础 URL（RSS/网页/SearXNG 实例等）。 */
  private String baseUrl;
  /** 超时秒数。 */
  private Integer timeoutSeconds;
  /** 仅保留最近 N 小时内的条目；null 表示不过滤。 */
  private Integer hours;
  /** User-Agent，可空。 */
  private String userAgent;
  /** 类型相关扩展配置。 */
  private ChannelFetchConfig config;

  /**
   * 从门面请求组装上下文。
   *
   * @param request 抓取请求
   * @param type    已解析的渠道类型
   * @return 策略上下文
   */
  public static ChannelFetchContext from(FetchRequest request, ChannelType type) {
    return ChannelFetchContext.builder()
        .type(type)
        .baseUrl(request.getUrl())
        .timeoutSeconds(request.getTimeoutSeconds())
        .hours(request.getHours())
        .userAgent(request.getUserAgent())
        .config(request.getConfig() == null ? new ChannelFetchConfig() : request.getConfig())
        .build();
  }

  /**
   * @return 超时秒数，缺省 {@link ChannelFetchDefaults#DEFAULT_TIMEOUT_SECONDS}
   */
  public int resolveTimeoutSeconds() {
    return timeoutSeconds == null || timeoutSeconds <= 0
        ? ChannelFetchDefaults.DEFAULT_TIMEOUT_SECONDS
        : timeoutSeconds;
  }

  /**
   * @return User-Agent，缺省成文坊爬虫标识
   */
  public String resolveUserAgent() {
    return userAgent == null || userAgent.isBlank()
        ? ChannelFetchDefaults.DEFAULT_USER_AGENT
        : userAgent;
  }
}
