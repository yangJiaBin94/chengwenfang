package com.workbench.crawler.application;

import com.workbench.crawler.facade.constant.ChannelFetchDefaults;
import com.workbench.infra.config.WorkbenchProperties;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/**
 * 爬虫 URL 安全校验与按主机限流（供各抓取策略复用）。
 */
@Component
public class CrawlerUrlGuard {
  /** Redisson 客户端，用于按主机限流。 */
  private final RedissonClient redisson;
  /** 是否允许环回/私网（测试用）。 */
  private final WorkbenchProperties properties;

  /**
   * @param redisson   Redis 客户端
   * @param properties 工作台配置
   */
  public CrawlerUrlGuard(RedissonClient redisson, WorkbenchProperties properties) {
    this.redisson = redisson;
    this.properties = properties;
  }

  /**
   * 校验 URL：仅允许 http(s)；默认拦截环回/私网（SSRF）。
   *
   * @param url 待校验地址
   * @throws Exception 协议非法或命中 SSRF 时
   */
  public void validateUrl(String url) throws Exception {
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException("URL 不能为空");
    }
    URI uri = URI.create(url);
    if (!ChannelFetchDefaults.SCHEME_HTTP.equalsIgnoreCase(uri.getScheme())
        && !ChannelFetchDefaults.SCHEME_HTTPS.equalsIgnoreCase(uri.getScheme())) {
      throw new IllegalArgumentException("仅允许 http/https");
    }
    if (properties.isCrawlerAllowLoopback()) {
      return;
    }
    InetAddress address = InetAddress.getByName(uri.getHost());
    if (address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()) {
      throw new IllegalArgumentException("SSRF 拦截主机：" + uri.getHost());
    }
  }

  /**
   * 对 URL 主机施加限流。
   *
   * @param url 目标 URL
   * @throws IllegalStateException 令牌不足时
   */
  public void limit(String url) {
    String host = URI.create(url).getHost();
    if (host == null) {
      return;
    }
    RRateLimiter limiter = redisson.getRateLimiter("crawler:host:" + host);
    // 每秒 HOST_RATE_PER_SECOND 个令牌
    limiter.trySetRate(
        RateType.OVERALL, ChannelFetchDefaults.HOST_RATE_PER_SECOND, Duration.ofSeconds(1));
    if (!limiter.tryAcquire(1)) {
      throw new IllegalStateException("已限流：" + host);
    }
  }
}
