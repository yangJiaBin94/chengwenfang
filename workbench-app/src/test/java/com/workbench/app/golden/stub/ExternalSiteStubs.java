package com.workbench.app.golden.stub;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.head;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.workbench.publishing.facade.constant.WechatMpApiPaths;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 外部站点固定响应：RSS、SearXNG、DuckDuckGo、GitHub、Tavily、文章页、公众号 API。
 * <p>场景侧把频道 / {@code search-base-url} / {@code wechat-mp.api-base} 指到本 WireMock。
 */
public final class ExternalSiteStubs {
  /** RSS 相对路径。 */
  public static final String PATH_RSS = "/golden/rss/feed.xml";
  /** SearXNG search 路径。 */
  public static final String PATH_SEARXNG = "/golden/searxng/search";
  /** GitHub repositories search。 */
  public static final String PATH_GITHUB = "/golden/github/search/repositories";
  /** Tavily search。 */
  public static final String PATH_TAVILY = "/golden/tavily/search";
  /** DuckDuckGo html 入口。 */
  public static final String PATH_DDG_HTML = "/html/";
  /** 示例文章页。 */
  public static final String PATH_ARTICLE = "/golden/article/sample";
  /** 示例视频页（路径含 /watch 与 v=，供资料池识别为视频）。 */
  public static final String PATH_VIDEO = "/golden/video/watch";

  private ExternalSiteStubs() {}

  /**
   * 骨架占位名。
   *
   * @return 类名
   */
  public String placeholderName() {
    return "ExternalSiteStubs";
  }

  /**
   * 启动并安装全部外部桩。
   *
   * @return 已 start 的 WireMock
   */
  public static WireMockServer startServer() {
    WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
    server.start();
    install(server);
    return server;
  }

  /**
   * 在已启动服务器上注册全部外部桩。
   *
   * @param server WireMock
   */
  public static void install(WireMockServer server) {
    String articleUrl = server.baseUrl() + PATH_ARTICLE;
    Instant recent = Instant.now().minusSeconds(3600);
    stubRss(server, articleUrl, recent);
    stubSearxng(server, articleUrl, recent);
    stubGithub(server, articleUrl, recent);
    stubTavily(server);
    stubDuckDuckGo(server, articleUrl);
    stubArticle(server);
    stubVideo(server);
    stubWechatMp(server);
  }

  private static void stubVideo(WireMockServer server) {
    String html =
        """
        <html><head><title>Golden Sleep Video: deep rest explained</title></head>
        <body><p>YouTube-style golden video page about sleep and melatonin for research pool parse.</p></body></html>
        """;
    server.stubFor(
        get(urlPathEqualTo(PATH_VIDEO))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/html; charset=utf-8")
                    .withBody(html)));
    server.stubFor(
        get(urlPathMatching(PATH_VIDEO + ".*"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/html; charset=utf-8")
                    .withBody(html)));
    server.stubFor(head(urlPathEqualTo(PATH_VIDEO)).willReturn(aResponse().withStatus(200)));
    server.stubFor(head(urlPathMatching(PATH_VIDEO + ".*")).willReturn(aResponse().withStatus(200)));
  }

  private static void stubRss(WireMockServer server, String articleUrl, Instant publishedAt) {
    String pubDate =
        DateTimeFormatter.RFC_1123_DATE_TIME
            .withLocale(Locale.US)
            .withZone(ZoneOffset.UTC)
            .format(publishedAt);
    String body =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0"><channel>
          <title>Golden Feed</title>
          <link>%s</link>
          <item>
            <title>Sleep mechanism study: how deep sleep restores memory</title>
            <link>%s</link>
            <pubDate>%s</pubDate>
            <description>Research brief on sleep mechanisms and melatonin for golden radar</description>
          </item>
        </channel></rss>
        """
            .formatted(articleUrl, articleUrl + "?from=rss", pubDate);
    server.stubFor(
        get(urlPathEqualTo(PATH_RSS))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/rss+xml; charset=utf-8")
                    .withBody(body)));
  }

  private static void stubSearxng(WireMockServer server, String articleUrl, Instant publishedAt) {
    String body =
        """
        {
          "results": [
            {
              "title": "Melatonin myth: does bedtime supplement really fix insomnia",
              "url": "%s",
              "content": "Sleep and melatonin research summary for golden radar",
              "publishedDate": "%s",
              "engines": ["bing"]
            }
          ]
        }
        """
            .formatted(articleUrl + "?from=searxng", publishedAt.toString());
    server.stubFor(
        get(urlPathEqualTo(PATH_SEARXNG))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBody(body)));
  }

  private static void stubGithub(WireMockServer server, String articleUrl, Instant publishedAt) {
    String body =
        """
        {
          "items": [
            {
              "full_name": "owner/golden-repo",
              "name": "golden-repo",
              "html_url": "%s",
              "description": "sleep melatonin research toolkit",
              "updated_at": "%s",
              "created_at": "2025-01-01T00:00:00Z"
            }
          ]
        }
        """
            .formatted(articleUrl + "?from=github", publishedAt.toString());
    server.stubFor(
        get(urlPathEqualTo(PATH_GITHUB))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBody(body)));
  }

  private static void stubTavily(WireMockServer server) {
    server.stubFor(
        post(urlPathEqualTo(PATH_TAVILY))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBody(readFixture("tavily-search.json"))));
  }

  private static void stubDuckDuckGo(WireMockServer server, String articleUrl) {
    String html = readFixture("duckduckgo-html.html").replace("{{ARTICLE_URL}}", articleUrl);
    server.stubFor(
        get(urlPathEqualTo(PATH_DDG_HTML))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/html; charset=utf-8")
                    .withBody(html)));
    server.stubFor(
        get(urlMatching("/html/\\?.*"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/html; charset=utf-8")
                    .withBody(html)));
  }

  private static void stubArticle(WireMockServer server) {
    String html = readFixture("article-sample.html");
    server.stubFor(
        get(urlPathEqualTo(PATH_ARTICLE))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/html; charset=utf-8")
                    .withBody(html)));
    // 带 query 的条目 URL 与可达性 HEAD
    server.stubFor(
        get(urlPathMatching(PATH_ARTICLE + ".*"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/html; charset=utf-8")
                    .withBody(html)));
    server.stubFor(head(urlPathEqualTo(PATH_ARTICLE)).willReturn(aResponse().withStatus(200)));
    server.stubFor(head(urlPathMatching(PATH_ARTICLE + ".*")).willReturn(aResponse().withStatus(200)));
  }

  private static void stubWechatMp(WireMockServer server) {
    server.stubFor(
        post(urlPathEqualTo(WechatMpApiPaths.STABLE_TOKEN))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(readFixture("wechat-stable-token.json"))));
    server.stubFor(
        post(urlPathMatching(WechatMpApiPaths.UPLOAD_IMAGE + ".*"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(readFixture("wechat-upload-img.json"))));
    server.stubFor(
        post(urlPathMatching(WechatMpApiPaths.ADD_MATERIAL + ".*"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(readFixture("wechat-add-material.json"))));
    server.stubFor(
        post(urlPathMatching(WechatMpApiPaths.DRAFT_ADD + ".*"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(readFixture("wechat-draft-add.json"))));
    server.stubFor(
        post(urlPathMatching(WechatMpApiPaths.DRAFT_UPDATE + ".*"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(readFixture("wechat-draft-update.json"))));
  }

  /**
   * 读取 external-fixtures 文本。
   *
   * @param name 文件名
   * @return UTF-8 文本
   */
  public static String readFixture(String name) {
    String path = "golden/external-fixtures/" + name;
    // 用本类 ClassLoader，避免 Hutool ResourceUtil 在长测程后偶发 NoResource
    try (java.io.InputStream stream =
        ExternalSiteStubs.class.getClassLoader().getResourceAsStream(path)) {
      if (stream == null) {
        // 回退 Hutool（兼容非标准 classpath 布局）
        String body = ResourceUtil.readStr(path, StandardCharsets.UTF_8);
        if (StrUtil.isBlank(body)) {
          throw new IllegalStateException("缺少外部 fixture: " + path);
        }
        return body;
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (java.io.IOException exception) {
      throw new IllegalStateException("读取外部 fixture 失败: " + path, exception);
    }
  }
}
