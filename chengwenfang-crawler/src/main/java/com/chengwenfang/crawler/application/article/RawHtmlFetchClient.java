package com.chengwenfang.crawler.application.article;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.chengwenfang.crawler.facade.HtmlDateTextSupport;
import com.chengwenfang.crawler.facade.constant.ArticleContentDefaults;
import com.chengwenfang.infra.config.ChengwenfangProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 本机直接下载源站 HTML，并转为日期抽取输入文本（Jina 抽空后的 Pass2）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RawHtmlFetchClient {
  /** 成文坊配置。 */
  private final ChengwenfangProperties properties;

  /**
   * 下载 HTML 并去标签。
   *
   * @param url 文章地址
   * @return dateInputText；失败返回空串
   */
  public String fetchDateInputText(String url) {
    if (StrUtil.isBlank(url)) {
      return "";
    }
    ChengwenfangProperties.ArticleContentSettings settings = properties.getArticleContent();
    int timeoutSeconds = settings == null || settings.getJinaTimeoutSeconds() <= 0
        ? ArticleContentDefaults.JINA_DEFAULT_TIMEOUT_SECONDS
        : settings.getJinaTimeoutSeconds();
    try (HttpResponse response = HttpRequest.get(url.trim())
        .header("User-Agent", ArticleContentDefaults.BROWSER_USER_AGENT)
        .header("Accept", "text/html,application/xhtml+xml,*/*")
        .timeout(timeoutSeconds * 1000)
        .execute()) {
      if (!response.isOk()) {
        log.info("原始 HTML 下载失败 status={} url={}", response.getStatus(), url);
        return "";
      }
      return HtmlDateTextSupport.toDateInputText(response.body());
    } catch (Exception exception) {
      log.info("原始 HTML 下载异常 url={} err={}", url, exception.getMessage());
      return "";
    }
  }
}
