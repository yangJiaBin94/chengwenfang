package com.chengwenfang.app.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.app.support.ChengwenfangIT;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "chengwenfang.worker-enabled=true",
    "chengwenfang.worker-concurrency=2"
})
class HotArticleRepairCandidateIT extends ChengwenfangIT {

  @Test
  void review_repair_accept_updates_hot_article() throws Exception {
    String themeId = "repair-" + UUID.randomUUID().toString().substring(0, 8);
    String topicId = UUID.randomUUID().toString();
    String briefId = UUID.randomUUID().toString();
    String articleId = UUID.randomUUID().toString();
    String badContent = "## 重复\n\n短文\n\n## 重复";

    jdbc.update(
        "INSERT INTO themes (id, mode_code, code, name, status, sort_order, created_via, created_at, updated_at) "
            + "VALUES (?,?,?,?,?,?,?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
        themeId, "hot_news", "repair-" + themeId, "修复主题", "enabled", 0, "test");
    jdbc.update(
        "INSERT INTO hotnews_topics (id, theme_id, title, summary, status, heat_score, writing_score, confidence_score, created_at, updated_at) "
            + "VALUES (?,?,?,?,?,?,?,?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
        topicId, themeId, "修复候选选题", "summary", "candidate", 1, 1, 1);
    jdbc.update(
        "INSERT INTO hotnews_briefs (id, topic_id, theme_id, status, title, created_at, updated_at) "
            + "VALUES (?,?,?,?,?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
        briefId, topicId, themeId, "confirmed", "修复候选选题");
    seedHotNewsArticleDocuments(
        articleId,
        topicId,
        briefId,
        "修复候选选题",
        "bad-hash",
        3,
        "ready",
        "{}",
        "[]");

    Map<String, Object> started = postJson("/api/hot-article-reviews", Map.of(
        "articleId", articleId,
        "content", badContent,
        "contentHash", "bad-hash",
        "sources", List.of(),
        "mode", "full"));
    String reviewId = String.valueOf(started.get("id"));
    Map<String, Object> reviewTerminal = awaitTerminal(reviewId);
    assertThat(reviewTerminal.get("status")).isEqualTo("succeeded");
    @SuppressWarnings("unchecked")
    Map<String, Object> reviewOutput = (Map<String, Object>) reviewTerminal.get("output");
    assertThat(reviewOutput.get("blockingFailed")).isEqualTo(true);

    Map<String, Object> latest = getJson("/api/articles/" + articleId + "/hot-reviews/latest");
    assertThat(latest.get("id")).isEqualTo(reviewId);
    assertThat((List<?>) latest.get("items")).isNotEmpty();

    Map<String, Object> repair = postJson("/api/hot-article-reviews/" + reviewId + "/repair-all");
    String repairJobId = String.valueOf(repair.get("id") != null ? repair.get("id") : repair.get("jobId"));
    Map<String, Object> repairTerminal = awaitTerminal(repairJobId);
    assertThat(repairTerminal.get("status")).isEqualTo("succeeded");
    @SuppressWarnings("unchecked")
    Map<String, Object> repairOutput = (Map<String, Object>) repairTerminal.get("output");
    String candidateId = String.valueOf(repairOutput.get("candidateId"));
    assertThat(candidateId).isNotBlank();

    Integer pending = jdbc.queryForObject(
        "SELECT COUNT(*) FROM repair_candidates WHERE id = ? AND status = 'pending'",
        Integer.class,
        candidateId);
    assertThat(pending).isEqualTo(1);

    Map<String, Object> accepted = postJson("/api/hot-article-repairs/" + candidateId + "/accept");
    assertThat(accepted.get("status")).isEqualTo("accepted");
    String revalidateJobId = String.valueOf(accepted.get("jobId"));
    assertThat(awaitTerminal(revalidateJobId).get("status")).isEqualTo("succeeded");

    Map<String, Object> article = getJson("/api/hot-news/articles/" + articleId);
    assertThat(article.get("content").toString()).contains("来源整理");
    assertThat(article.get("content").toString()).doesNotContain("## 重复\n\n短文\n\n## 重复");

    String candidateStatus = jdbc.queryForObject(
        "SELECT status FROM repair_candidates WHERE id = ?",
        String.class,
        candidateId);
    assertThat(candidateStatus).isEqualTo("accepted");
  }
}
