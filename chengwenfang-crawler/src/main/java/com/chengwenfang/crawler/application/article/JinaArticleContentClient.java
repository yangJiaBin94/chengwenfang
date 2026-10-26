package com.chengwenfang.crawler.application.article;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.chengwenfang.crawler.facade.constant.ArticleContentDefaults;
import com.chengwenfang.infra.config.ChengwenfangProperties;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 通过 Jina Reader 将 URL 转为正文 Markdown。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JinaArticleContentClient {
  /** 工作台配置。 */
  private final ChengwenfangProperties properties;

  /**
   * @param url 文章地址
   * @return 正文；失败返回空串（兼容旧调用方）
   */
  public String fetchText(String url) {
    return StrUtil.nullToEmpty(fetch(url).getText());
  }

  /**
   * 拉取正文，并区分超时以便上层回退原生爬取。
   *
   * @param url 文章地址
   * @return 结构化结果
   */
  public JinaFetchResult fetch(String url) {
    ChengwenfangProperties.ArticleContentSettings settings = properties.getArticleContent();
    if (settings == null || !settings.isJinaEnabled() || StrUtil.isBlank(url)) {
      return JinaFetchResult.failure(false, "Jina 未启用或 URL 为空");
    }
    String base = StrUtil.blankToDefault(settings.getJinaBase(), ArticleContentDefaults.JINA_DEFAULT_BASE)
        .trim();
    if (!base.endsWith("/")) {
      base = base + "/";
    }
    String target = base + url.trim();
    int readTimeoutSeconds = settings.getJinaTimeoutSeconds() <= 0
        ? ArticleContentDefaults.JINA_DEFAULT_TIMEOUT_SECONDS
        : settings.getJinaTimeoutSeconds();
    int connectTimeoutSeconds = settings.getJinaConnectTimeoutSeconds() <= 0
        ? ArticleContentDefaults.JINA_DEFAULT_CONNECT_TIMEOUT_SECONDS
        : settings.getJinaConnectTimeoutSeconds();
    try (HttpResponse response = HttpRequest.get(target)
        .header("User-Agent", ArticleContentDefaults.BROWSER_USER_AGENT)
        .header("Accept", "text/plain,text/markdown,*/*")
        .setConnectionTimeout(connectTimeoutSeconds * 1000)
        .setReadTimeout(readTimeoutSeconds * 1000)
        .execute()) {
      if (!response.isOk()) {
        log.info("Jina 正文失败 status={} jinaHost={} url={}", response.getStatus(), base, url);
        return JinaFetchResult.failure(false, "HTTP " + response.getStatus());
      }
      return JinaFetchResult.success(response.body());
    } catch (Exception exception) {
      boolean timedOut = isTimeout(exception);
      String message = StrUtil.blankToDefault(exception.getMessage(), exception.getClass().getSimpleName());
      log.info(
          "Jina 正文异常 timedOut={} jinaHost={} url={} err={}",
          timedOut,
          base,
          url,
          message);
      return JinaFetchResult.failure(timedOut, message);
    }
  }

  private static boolean isTimeout(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      String name = current.getClass().getName().toLowerCase(Locale.ROOT);
      String message = StrUtil.nullToEmpty(current.getMessage()).toLowerCase(Locale.ROOT);
      if (name.contains("timeout")
          || message.contains("timed out")
          || message.contains("timeout")
          || message.contains("connect timed out")
          || message.contains("read timed out")) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }
}
