package com.workbench.app.e2e;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.workbench.app.support.WorkbenchIT;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "workbench.worker-enabled=true",
    "workbench.worker-concurrency=2",
    "workbench.crawler-allow-loopback=true"
})
class TopicResearchE2EIT extends WorkbenchIT {

  private static final WireMockServer wireMock;

  static {
    wireMock = new WireMockServer(wireMockConfig().dynamicPort());
    wireMock.start();
  }

  @AfterAll
  static void stopWireMock() {
    wireMock.stop();
  }

  @DynamicPropertySource
  static void searchBase(DynamicPropertyRegistry registry) {
    registry.add("workbench.search-base-url", wireMock::baseUrl);
  }

  @BeforeEach
  void resetWireMock() {
    wireMock.resetAll();
  }

  @Test
  void topic_research_uses_search_and_persists_items() throws Exception {
    String articleUrl = wireMock.baseUrl() + "/article/alpha";
    wireMock.stubFor(get(urlPathEqualTo("/html/"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "text/html; charset=utf-8")
            .withBody("""
                <html><body>
                  <div class="result">
                    <a class="result__a" href="%s">Alpha Research Hit</a>
                    <a class="result__snippet">Snippet about Alpha</a>
                  </div>
                </body></html>
                """.formatted(articleUrl))));
    wireMock.stubFor(get(urlMatching("/html/\\?.*"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "text/html; charset=utf-8")
            .withBody("""
                <html><body>
                  <div class="result">
                    <a class="result__a" href="%s">Alpha Research Hit</a>
                    <div class="result__snippet">Snippet about Alpha</div>
                  </div>
                </body></html>
                """.formatted(articleUrl))));
    wireMock.stubFor(get(urlPathEqualTo("/article/alpha"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "text/html; charset=utf-8")
            .withBody("""
                <html><head><title>Alpha Page Title</title></head>
                <body><article><p>Deep dive into Alpha research findings.</p></article></body></html>
                """)));

    String channelId = UUID.randomUUID().toString();
    String themeId = "it-research-" + channelId.substring(0, 8);
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    String rss = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0"><channel>
          <title>Research Feed</title>
          <item>
            <title>Alpha headline for research</title>
            <link>https://example.com/research-a</link>
            <pubDate>%s</pubDate>
            <description>seed</description>
          </item>
        </channel></rss>
        """.formatted(DateTimeFormatter.RFC_1123_DATE_TIME
        .withLocale(Locale.US)
        .format(now.atOffset(ZoneOffset.UTC)));

    wireMock.stubFor(get(urlPathEqualTo("/feed.xml"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/rss+xml")
            .withBody(rss)));

    putJson("/api/hot-news/channels", Map.of(
        "id", channelId,
        "themeId", themeId,
        "name", "research-rss",
        "type", "rss",
        "baseUrl", wireMock.baseUrl() + "/feed.xml",
        "enabled", true));

    Map<String, Object> fetchJob = postJson("/api/hot-news/fetch", Map.of(
        "themeId", themeId,
        "channelIds", List.of(channelId),
        "hours", 72));
    assertThat(awaitTerminal(String.valueOf(fetchJob.get("jobId"))).get("status")).isEqualTo("succeeded");

    List<Map<String, Object>> topics = getJsonList("/api/hot-news/topics?themeId=" + themeId);
    assertThat(topics).isNotEmpty();
    String topicId = String.valueOf(topics.get(0).get("id"));

    Map<String, Object> researchJob = postJson("/api/hot-news/topics/" + topicId + "/research-runs");
    String researchJobId = String.valueOf(
        researchJob.get("id") != null ? researchJob.get("id") : researchJob.get("jobId"));
    Map<String, Object> terminal = awaitTerminal(researchJobId, 40);
    assertThat(terminal.get("status")).isEqualTo("succeeded");

    List<Map<String, Object>> items = getJsonList("/api/hot-news/topics/" + topicId + "/research-items");
    assertThat(items).isNotEmpty();
    assertThat(items).anyMatch(item -> {
      String title = String.valueOf(item.get("title"));
      String url = String.valueOf(item.get("url"));
      return title.contains("Alpha") || url.contains("article/alpha") || url.contains("example.com");
    });
  }
}
