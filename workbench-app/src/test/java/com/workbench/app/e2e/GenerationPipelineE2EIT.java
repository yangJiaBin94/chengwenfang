package com.workbench.app.e2e;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.workbench.app.support.WorkbenchIT;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "workbench.worker-enabled=true",
    "workbench.worker-concurrency=2"
})
class GenerationPipelineE2EIT extends WorkbenchIT {

  private static WireMockServer wireMock;

  @BeforeAll
  static void startWireMock() {
    wireMock = new WireMockServer(wireMockConfig().dynamicPort());
    wireMock.start();
  }

  @AfterAll
  static void stopWireMock() {
    if (wireMock != null) {
      wireMock.stop();
    }
  }

  @BeforeEach
  void resetWireMock() {
    wireMock.resetAll();
  }

  @Test
  void course_generate_content_persists_non_empty_draft_and_article() throws Exception {
    String courseId = "gc-" + UUID.randomUUID().toString().substring(0, 8);
    Map<String, Object> started = postJson("/api/courses/" + courseId + "/generate-content", Map.of());
    String jobId = String.valueOf(started.get("id") != null ? started.get("id") : started.get("jobId"));

    Map<String, Object> terminal = awaitTerminal(jobId);
    assertThat(terminal.get("status")).isEqualTo("succeeded");

    Map<String, Object> content = getJson("/api/courses/" + courseId + "/content");
    assertThat(content.get("draft").toString()).isNotBlank().contains("本课小结");
    assertThat(content.get("article").toString()).isNotBlank().contains("[[COURSE_COVER]]");
  }

  @Test
  void hot_news_brief_to_article_has_content() throws Exception {
    String channelId = UUID.randomUUID().toString();
    String themeId = "it-gen-" + channelId.substring(0, 8);
    stubRss("/gen-feed.xml", List.of(
        entry("生成链路 Alpha 发布", "https://example.com/gen-a", LocalDateTime.now().minusSeconds(3600)),
        entry("生成链路 Beta 解读", "https://example.com/gen-b", LocalDateTime.now().minusSeconds(1800))));

    putJson("/api/hot-news/channels", Map.of(
        "id", channelId,
        "themeId", themeId,
        "name", "gen-rss",
        "type", "rss",
        "baseUrl", wireMock.baseUrl() + "/gen-feed.xml",
        "enabled", true));

    Map<String, Object> fetch = postJson("/api/hot-news/fetch", Map.of(
        "themeId", themeId,
        "channelIds", List.of(channelId),
        "hours", 72));
    assertThat(awaitTerminal(String.valueOf(fetch.get("jobId"))).get("status")).isEqualTo("succeeded");

    List<Map<String, Object>> topics = getJsonList("/api/hot-news/topics?themeId=" + themeId);
    assertThat(topics).isNotEmpty();
    String topicId = String.valueOf(topics.get(0).get("id"));

    Map<String, Object> brief = postJson("/api/hot-news/topics/" + topicId + "/briefs");
    String briefId = String.valueOf(brief.get("id"));
    Map<String, Object> confirmed = postJson(
        "/api/hot-news/briefs/" + briefId + "/confirm",
        Map.of("note", "e2e confirm"));
    assertThat(confirmed.get("status")).isEqualTo("confirmed");

    Map<String, Object> started = postJson("/api/hot-news/briefs/" + briefId + "/generate-article");
    String jobId = String.valueOf(started.get("id") != null ? started.get("id") : started.get("jobId"));
    Map<String, Object> terminal = awaitTerminal(jobId);
    assertThat(terminal.get("status")).isEqualTo("succeeded");

    List<Map<String, Object>> articles = getJsonList("/api/hot-news/topics/" + topicId + "/articles");
    assertThat(articles).isNotEmpty();
    String articleId = String.valueOf(articles.get(0).get("id"));
    Map<String, Object> article = getJson("/api/hot-news/articles/" + articleId);
    assertThat(article.get("content").toString()).isNotBlank();
    assertThat(article.get("draftContent") == null ? "" : article.get("draftContent").toString()).isNotBlank();
  }

  @Test
  void hot_article_review_http_job_returns_results() throws Exception {
    String articleId = "review-article-" + UUID.randomUUID();
    String content = """
        # 标题有效文章

        ## 背景说明
        这是一段足够长的正文内容，用来通过最小长度检查，并避免空段落阻断。

        ## 影响分析
        读者可以据此判断事件影响，并找到可执行的下一步。

        ## 来源整理
        1. Example News - 原始报道 - https://example.com/source
        """;

    Map<String, Object> started = postJson("/api/hot-article-reviews", Map.of(
        "articleId", articleId,
        "content", content,
        "contentHash", "hash-" + articleId,
        "sources", List.of(Map.of(
            "title", "原始报道",
            "url", "https://example.com/source",
            "sourceName", "Example News")),
        "mode", "full"));
    assertThat(started.get("id")).isNotNull();
    assertThat(started.get("jobId")).isEqualTo(started.get("id"));
    assertThat(started.get("status")).isIn("pending", "queued", "running", "succeeded");

    String jobId = String.valueOf(started.get("id"));
    Map<String, Object> terminal = awaitTerminal(jobId);
    assertThat(terminal.get("status")).isEqualTo("succeeded");
    @SuppressWarnings("unchecked")
    Map<String, Object> output = (Map<String, Object>) terminal.get("output");
    assertThat(output.get("results")).isInstanceOf(List.class);
    assertThat((List<?>) output.get("results")).isNotEmpty();

    Map<String, Object> latest = getJson("/api/articles/" + articleId + "/hot-reviews/latest");
    assertThat(latest.get("id")).isEqualTo(jobId);
    assertThat(latest.get("status")).isNotEqualTo("none");
    assertThat((List<?>) latest.get("items")).isNotEmpty();

    Map<String, Object> none = getJson("/api/articles/missing-" + UUID.randomUUID() + "/hot-reviews/latest");
    assertThat(none.get("status")).isEqualTo("none");
    assertThat(none.get("id")).isNull();

    Map<String, Object> decision = postJson(
        "/api/hot-article-reviews/" + jobId + "/decision",
        Map.of("status", "approved", "comment", "ok"));
    assertThat(decision.get("ok")).isEqualTo(true);
  }

  private void stubRss(String path, List<RssEntry> entries) {
    wireMock.stubFor(get(urlEqualTo(path))
        .willReturn(aResponse()
            .withHeader("Content-Type", "application/rss+xml; charset=utf-8")
            .withBody(toRss(entries))));
  }

  private static String toRss(List<RssEntry> entries) {
    DateTimeFormatter rfc822 = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
    StringBuilder sb = new StringBuilder();
    sb.append("""
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0"><channel>
          <title>Gen Feed</title>
          <link>https://example.com</link>
          <description>fixture</description>
        """);
    for (RssEntry entry : entries) {
      sb.append("<item>")
          .append("<title>").append(entry.title()).append("</title>")
          .append("<link>").append(entry.link()).append("</link>")
          .append("<pubDate>").append(rfc822.format(entry.published())).append("</pubDate>")
          .append("<description>").append(entry.title()).append(" summary</description>")
          .append("</item>");
    }
    sb.append("</channel></rss>");
    return sb.toString();
  }

  private static RssEntry entry(String title, String link, LocalDateTime published) {
    return new RssEntry(title, link, published);
  }

  /**
   * RSS 桩数据条目：标题、链接与发布时间。
   */
  private static final class RssEntry {
    /** 条目标题。 */
    private final String title;
    /** 条目链接。 */
    private final String link;
    /** 发布时间。 */
    private final LocalDateTime published;

    /**
     * @param title 标题
     * @param link 链接
     * @param published 发布时间
     */
    private RssEntry(String title, String link, LocalDateTime published) {
      this.title = title;
      this.link = link;
      this.published = published;
    }

    /** @return 标题 */
    private String title() {
      return title;
    }

    /** @return 链接 */
    private String link() {
      return link;
    }

    /** @return 发布时间 */
    private LocalDateTime published() {
      return published;
    }
  }
}
