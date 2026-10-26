package com.workbench.app.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.workbench.app.support.WorkbenchIT;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "workbench.worker-enabled=true",
    "workbench.worker-concurrency=2"
})
class HotNewsWorkspaceApiIT extends WorkbenchIT {

  @Test
  void channel_patch_content_patch_and_versions_list() throws Exception {
    String channelId = UUID.randomUUID().toString();
    String themeId = "ws-" + channelId.substring(0, 8);
    jdbc.update(
        "INSERT INTO themes (id, mode_code, code, name, status, sort_order, created_via, created_at, updated_at) "
            + "VALUES (?,?,?,?,?,?,?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
        themeId, "hot_news", "ws-" + themeId, "工作台主题", "enabled", 0, "test");

    Map<String, Object> created = putJson("/api/hot-news/channels", Map.of(
        "id", channelId,
        "themeId", themeId,
        "name", "workspace-rss",
        "type", "rss",
        "baseUrl", "https://example.com/feed.xml",
        "enabled", true));
    assertThat(created.get("id")).isEqualTo(channelId);
    assertThat(created.containsKey("trustLevel")).isFalse();

    Map<String, Object> patched = patchJson("/api/hot-news/channels/" + channelId, Map.of(
        "name", "workspace-rss-renamed",
        "baseUrl", "https://example.com/renamed.xml",
        "enabled", false));
    assertThat(patched.get("name")).isEqualTo("workspace-rss-renamed");
    assertThat(patched.get("baseUrl")).isEqualTo("https://example.com/renamed.xml");
    assertThat(patched.get("enabled")).isEqualTo(false);
    assertThat(patched.containsKey("trustLevel")).isFalse();

    Map<String, Object> deleted = deleteJson("/api/hot-news/channels/" + channelId);
    assertThat(deleted.get("enabled")).isEqualTo(false);
    assertThat(deleted.get("deleted")).isEqualTo(true);

    String topicId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO hotnews_topics (id, theme_id, title, summary, status, heat_score, writing_score, confidence_score, created_at, updated_at) "
            + "VALUES (?,?,?,?,?,?,?,?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
        topicId, themeId, "工作台编辑选题", "summary", "candidate", 1, 1, 1);
    String briefId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO hotnews_briefs (id, topic_id, theme_id, status, title, created_at, updated_at) "
            + "VALUES (?,?,?,?,?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
        briefId, topicId, themeId, "confirmed", "工作台编辑选题");
    String articleId = UUID.randomUUID().toString();
    seedHotNewsArticleDocuments(
        articleId,
        topicId,
        briefId,
        "工作台编辑选题",
        "e3b0c442",
        0,
        "ready",
        "{\"wordCountOption\":\"2500-4000\"}",
        "[]");

    Map<String, Object> draftPatched = patchJson(
        "/api/hot-news/articles/" + articleId + "/contents/draft",
        Map.of("content", "# 草稿\n\n第一版草稿正文"));
    assertThat(draftPatched.get("draftContent").toString()).contains("第一版草稿正文");

    Map<String, Object> wechatPatched = patchJson(
        "/api/hot-news/articles/" + articleId + "/contents/wechat",
        Map.of("content", "# 公众号\n\n第一版公众号正文"));
    assertThat(wechatPatched.get("content").toString()).contains("第一版公众号正文");
    assertThat(((Number) wechatPatched.get("currentVersionNo")).intValue()).isGreaterThanOrEqualTo(3);

    Map<String, Object> settings = patchJson(
        "/api/hot-news/articles/" + articleId + "/generation-settings",
        Map.of("wordCountOption", "3000-5000", "tone", "sharp"));
    assertThat(settings.get("generationSettings")).isInstanceOf(Map.class);
    @SuppressWarnings("unchecked")
    Map<String, Object> gen = (Map<String, Object>) settings.get("generationSettings");
    assertThat(gen.get("wordCountOption")).isEqualTo("3000-5000");
    assertThat(gen.get("tone")).isEqualTo("sharp");

    List<Map<String, Object>> versions = getJsonList("/api/hot-news/articles/" + articleId + "/versions");
    assertThat(versions).isNotEmpty();
    assertThat(versions.get(0).get("versionNo")).isNotNull();
  }
}
