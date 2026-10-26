package com.chengwenfang.crawler.application.article;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.chengwenfang.crawler.facade.constant.ArticleContentDefaults;
import com.chengwenfang.infra.config.ChengwenfangProperties;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 探测 URL 是否可被本机直接打开（不经 Jina），供雷达获取阶段过滤死链。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UrlReachabilityProbe {
  /** 成文坊配置。 */
  private final ChengwenfangProperties properties;

  /**
   * 探测结果。
   */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  public static final class ProbeResult {
    /** 是否可打开。 */
    private final boolean reachable;
    /** HTTP 状态；连接失败为 0。 */
    private final int status;
    /** 失败原因；成功为空。 */
    private final String reason;

    /** 成功占位。 */
    public static ProbeResult ok(int status) {
      return new ProbeResult(true, status, "");
    }

    /** 失败占位。 */
    public static ProbeResult fail(String reason) {
      return new ProbeResult(false, 0, StrUtil.blankToDefault(reason, "不可达"));
    }
  }

  /**
   * 本机直连探测：能建立 HTTP(S) 且状态非明确死链（404/410）即视为可打开。
   *
   * @param url 候选链接
   * @return 探测结果
   */
  public ProbeResult probe(String url) {
    if (StrUtil.isBlank(url)) {
      return ProbeResult.fail("URL 为空");
    }
    String trimmed = url.trim();
    if (!looksLikeHttpUrl(trimmed)) {
      return ProbeResult.fail("非 HTTP(S) 链接");
    }
    // 配置/常量跳过域名：不探测，直接视为不可用
    if (isSkipHost(trimmed)) {
      return ProbeResult.fail("命中 skip-hosts");
    }
    int timeoutMs = probeTimeoutMillis();
    // 先 HEAD（轻量）；部分站点不支持时再 GET
    ProbeResult head = request(trimmed, true, timeoutMs);
    if (head.reachable()) {
      return head;
    }
    if (shouldFallbackToGet(head)) {
      return request(trimmed, false, timeoutMs);
    }
    return head;
  }

  /**
   * @param url 链接
   * @return 是否可打开
   */
  public boolean isReachable(String url) {
    return probe(url).reachable();
  }

  /**
   * 是否命中 {@code chengwenfang.article-content.skip-hosts}（仅配置，无代码兜底）。
   *
   * @param url 链接
   * @return true 表示应跳过远端抓取
   */
  public boolean isSkipHost(String url) {
    if (StrUtil.isBlank(url)) {
      return false;
    }
    ChengwenfangProperties.ArticleContentSettings settings = properties.getArticleContent();
    List<String> skipHosts = settings == null ? null : settings.getSkipHosts();
    if (CollUtil.isEmpty(skipHosts)) {
      return false;
    }
    String host = HostConcurrencyGate.hostKey(url);
    for (String skip : skipHosts) {
      if (StrUtil.isBlank(skip)) {
        continue;
      }
      String needle = skip.trim().toLowerCase(Locale.ROOT);
      if (host.equals(needle) || host.endsWith("." + needle)) {
        return true;
      }
    }
    return false;
  }

  private ProbeResult request(String url, boolean head, int timeoutMs) {
    String method = head ? "HEAD" : "GET";
    try (HttpResponse response = (head ? HttpRequest.head(url) : HttpRequest.get(url))
        .header("User-Agent", ArticleContentDefaults.BROWSER_USER_AGENT)
        .header("Accept", "*/*")
        .setFollowRedirects(true)
        .setConnectionTimeout(timeoutMs)
        .setReadTimeout(timeoutMs)
        .execute()) {
      int status = response.getStatus();
      if (isDeadStatus(status)) {
        log.info("URL 直连判定不可用 method={} status={} url={}", method, status, url);
        return ProbeResult.fail("HTTP " + status);
      }
      // GET 时识别安全验证/验证码壳页（部分站点仍返回 200）
      if (!head && looksLikeSecurityChallenge(response.body())) {
        log.info("URL 直连判定安全验证页 method={} status={} url={}", method, status, url);
        return ProbeResult.fail("安全验证/验证码页");
      }
      return ProbeResult.ok(status);
    } catch (Exception exception) {
      String message = StrUtil.blankToDefault(exception.getMessage(), exception.getClass().getSimpleName());
      log.info("URL 直连失败 method={} url={} err={}", method, url, message);
      return ProbeResult.fail(message);
    }
  }

  private int probeTimeoutMillis() {
    ChengwenfangProperties.ArticleContentSettings settings = properties.getArticleContent();
    int seconds = settings == null ? ArticleContentDefaults.URL_PROBE_DEFAULT_TIMEOUT_SECONDS
        : settings.getUrlProbeTimeoutSeconds();
    if (seconds <= 0) {
      seconds = ArticleContentDefaults.URL_PROBE_DEFAULT_TIMEOUT_SECONDS;
    }
    return seconds * 1000;
  }

  private static boolean looksLikeHttpUrl(String url) {
    try {
      URI uri = URI.create(url);
      String scheme = StrUtil.nullToEmpty(uri.getScheme()).toLowerCase(Locale.ROOT);
      return "http".equals(scheme) || "https".equals(scheme);
    } catch (Exception ignored) {
      return false;
    }
  }

  private static boolean isDeadStatus(int status) {
    // 401/403：登录墙或安全验证；404/410：资源不存在
    return status == 401 || status == 403 || status == 404 || status == 410;
  }

  /**
   * 是否像验证码 / 安全验证壳页（无正文可抽）。
   *
   * @param body 响应体
   * @return true 表示不可用
   */
  private static boolean looksLikeSecurityChallenge(String body) {
    if (StrUtil.isBlank(body)) {
      return false;
    }
    String lower = body.toLowerCase(Locale.ROOT);
    return lower.contains("百度安全验证")
        || lower.contains("安全验证")
        || lower.contains("captcha")
        || lower.contains("请完成验证")
        || lower.contains("access denied")
        || (lower.contains("verify") && lower.contains("challenge"));
  }

  private static boolean shouldFallbackToGet(ProbeResult head) {
    if (head == null || head.reachable()) {
      return false;
    }
    String reason = StrUtil.nullToEmpty(head.reason()).toLowerCase(Locale.ROOT);
    // 网络层失败无需再 GET，避免双倍等待
    if (reason.contains("timed out")
        || reason.contains("timeout")
        || reason.contains("connection refused")
        || reason.contains("unknown host")
        || reason.contains("unreachable")) {
      return false;
    }
    // 方法不允许 / 空响应等：改用 GET 再探一次
    return reason.contains("405")
        || reason.contains("501")
        || reason.contains("method")
        || reason.contains("unexpected end")
        || StrUtil.isBlank(head.reason());
  }
}
