package com.chengwenfang.app.e2e;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.chengwenfang.app.support.ChengwenfangIT;
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
    "chengwenfang.worker-enabled=true",
    "chengwenfang.worker-concurrency=2"
})
class HotNewsFetchE2EIT extends ChengwenfangIT {

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
  void channel_test_and_fetch_persists_rss_items() throws Exception {
    String channelId = UUID.randomUUID().toString();
    String themeId = "it-hot-" + channelId.substring(0, 8);
    stubRss("/feed.xml", List.of(
        entry("Alpha headline", "https://example.com/a", LocalDateTime.now().minusSeconds(3600)),
        entry("Beta headline", "https://example.com/b", LocalDateTime.now().minusSeconds(1800))));

    Map<String, Object> channel = putJson("/api/hot-news/channels", Map.of(
        "id", channelId,
        "themeId", themeId,
        "name", "IT RSS",
        "type", "rss",
        "baseUrl", wireMock.baseUrl() + "/feed.xml",
        "enabled", true));
    assertThat(channel.get("id")).isEqualTo(channelId);

    Map<String, Object> test = postJson("/api/hot-news/channels/" + channelId + "/test");
    assertThat(test.get("ok")).isEqualTo(true);
    assertThat(((Number) test.get("itemCount")).intValue()).isGreaterThanOrEqualTo(1);

    Map<String, Object> started = postJson("/api/hot-news/fetch", Map.of(
        "themeId", themeId,
        "channelIds", List.of(channelId),
        "hours", 72));
    String jobId = String.valueOf(started.get("jobId"));

    Map<String, Object> terminal = awaitTerminal(jobId);
    assertThat(terminal.get("status")).isEqualTo("succeeded");
    assertThat(terminal.get("progress")).isEqualTo(100);

    List<Map<String, Object>> events = eventHistory(jobId);
    assertThat(events).extracting(e -> e.get("eventType"))
        .contains("queued", "started", "progress", "succeeded");
    assertThat(events).anyMatch(e -> "fetch".equals(e.get("step")));
    assertThat(events).anyMatch(e -> "topic_aggregate".equals(e.get("step")));

    List<Map<String, Object>> items = getJsonList("/api/hot-news/items?themeId=" + themeId + "&limit=50");
    assertThat(items).hasSizeGreaterThanOrEqualTo(2);
    assertThat(items).extracting(i -> i.get("title"))
        .contains("Alpha headline", "Beta headline");
    assertThat(items).allMatch(i -> channelId.equals(i.get("channelId")));

    @SuppressWarnings("unchecked")
    Map<String, Object> output = (Map<String, Object>) terminal.get("output");
    assertThat(((Number) output.get("newItems")).intValue()).isGreaterThanOrEqualTo(2);
    assertThat(((Number) output.get("topicCount")).intValue()).isGreaterThanOrEqualTo(1);

    List<Map<String, Object>> topics = getJsonList("/api/hot-news/topics?themeId=" + themeId);
    assertThat(topics).isNotEmpty();
  }

  @Test
  void fetch_deduplicates_same_url_and_clusters_similar_titles() throws Exception {
    String channelId = UUID.randomUUID().toString();
    String themeId = "it-dedupe-" + channelId.substring(0, 8);
    LocalDateTime now = LocalDateTime.now();
    stubRss("/dup.xml", List.of(
        entry("OpenAI 发布新的 AI 模型", "https://example.com/openai", now.minusSeconds(1200)),
        entry("OpenAI 发布新 AI 模型", "https://example.com/openai-2", now.minusSeconds(600)),
        entry("OpenAI 发布新的 AI 模型", "https://example.com/openai", now.minusSeconds(300))));

    putJson("/api/hot-news/channels", Map.of(
        "id", channelId,
        "themeId", themeId,
        "name", "dedupe",
        "type", "rss",
        "baseUrl", wireMock.baseUrl() + "/dup.xml",
        "enabled", true));

    Map<String, Object> first = postJson("/api/hot-news/fetch", Map.of(
        "themeId", themeId,
        "channelIds", List.of(channelId),
        "hours", 72));
    Map<String, Object> terminal = awaitTerminal(String.valueOf(first.get("jobId")));
    @SuppressWarnings("unchecked")
    Map<String, Object> output = (Map<String, Object>) terminal.get("output");
    assertThat(((Number) output.get("newItems")).intValue()).isEqualTo(2);
    assertThat(((Number) output.get("dedupedItems")).intValue()).isEqualTo(1);

    // Re-fetch same feed: all should dedupe.
    Map<String, Object> second = postJson("/api/hot-news/fetch", Map.of(
        "themeId", themeId,
        "channelIds", List.of(channelId),
        "hours", 72));
    Map<String, Object> terminal2 = awaitTerminal(String.valueOf(second.get("jobId")));
    @SuppressWarnings("unchecked")
    Map<String, Object> output2 = (Map<String, Object>) terminal2.get("output");
    assertThat(((Number) output2.get("newItems")).intValue()).isEqualTo(0);
    assertThat(((Number) output2.get("dedupedItems")).intValue()).isEqualTo(3);

    List<Map<String, Object>> items = getJsonList("/api/hot-news/items?themeId=" + themeId);
    assertThat(items).hasSize(2);

    List<Map<String, Object>> topics = getJsonList("/api/hot-news/topics?themeId=" + themeId);
    assertThat(topics).hasSize(1);
    assertThat(topics.get(0).get("title").toString()).contains("OpenAI");
    assertThat(((Number) topics.get(0).get("heatScore")).intValue()).isGreaterThan(0);
    // 旧 fetch 路径仅写入采集事实分；写作价值由雷达 SCORE_TOP10 按主题量表 AI 评分
    assertThat(topics.get(0).get("writingScore")).isNotNull();
    assertThat(topics.get(0).get("scoreReason").toString()).contains("来源");
  }

  @Test
  void fetch_isolates_failing_channel_and_keeps_good_items() throws Exception {
    String goodId = UUID.randomUUID().toString();
    String badId = UUID.randomUUID().toString();
    String themeId = "it-mix-" + goodId.substring(0, 8);

    stubRss("/good.xml", List.of(
        entry("Good only", "https://example.com/good", LocalDateTime.now().minusSeconds(600))));
    wireMock.stubFor(get(urlEqualTo("/bad.xml"))
        .willReturn(aResponse().withStatus(500).withBody("boom")));

    putJson("/api/hot-news/channels", Map.of(
        "id", goodId,
        "themeId", themeId,
        "name", "good",
        "type", "rss",
        "baseUrl", wireMock.baseUrl() + "/good.xml",
        "enabled", true));
    putJson("/api/hot-news/channels", Map.of(
        "id", badId,
        "themeId", themeId,
        "name", "bad",
        "type", "rss",
        "baseUrl", wireMock.baseUrl() + "/bad.xml",
        "enabled", true));

    Map<String, Object> started = postJson("/api/hot-news/fetch", Map.of(
        "themeId", themeId,
        "channelIds", List.of(goodId, badId),
        "hours", 72));
    String jobId = String.valueOf(started.get("jobId"));

    Map<String, Object> terminal = awaitTerminal(jobId);
    assertThat(terminal.get("status")).isEqualTo("succeeded");

    List<Map<String, Object>> events = eventHistory(jobId);
    assertThat(events).anyMatch(e -> "channel_failed".equals(e.get("eventType")));

    List<Map<String, Object>> items = getJsonList("/api/hot-news/items?themeId=" + themeId);
    assertThat(items).extracting(i -> i.get("title")).containsExactly("Good only");
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
          <title>IT Feed</title>
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
