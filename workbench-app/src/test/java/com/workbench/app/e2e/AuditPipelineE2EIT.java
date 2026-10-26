package com.workbench.app.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.workbench.app.support.WorkbenchIT;
import com.workbench.job.facade.dto.view.JobView;
import com.workbench.job.facade.constant.JobTypes;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "workbench.worker-enabled=true",
    "workbench.worker-concurrency=2"
})
class AuditPipelineE2EIT extends WorkbenchIT {

  @Test
  void audit_text_returns_deterministic_rule_report() throws Exception {
    Map<String, Object> result = postJson(
        "/api/articles/audit-text",
        Map.of("text", "## 行动\n路径 -> 结果"));
    assertThat(result.get("blocking")).isEqualTo(true);
    assertThat(((Number) result.get("score")).intValue()).isLessThan(100);
    assertThat((List<?>) result.get("issues")).isNotEmpty();
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
    assertThat(items).extracting(item -> item.get("code"))
        .contains("FORBIDDEN_SYMBOL", "IMAGE_ASSETS_PRESENT", "NO_AI_CLICHES", "NO_CODE_FENCE");
  }

  @Test
  void audit_text_flags_knowledge_course_number_and_code_fence() throws Exception {
    Map<String, Object> result = postJson(
        "/api/articles/audit-text",
        Map.of("text", "# 第3课：入门\n\n```js\nconsole.log(1)\n```\n"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
    Map<String, Object> courseNumber = items.stream()
        .filter(item -> "TITLE_NO_COURSE_NUMBER".equals(item.get("code")))
        .findFirst()
        .orElseThrow();
    Map<String, Object> fence = items.stream()
        .filter(item -> "NO_CODE_FENCE".equals(item.get("code")))
        .findFirst()
        .orElseThrow();
    assertThat(courseNumber.get("status")).isEqualTo("fail");
    assertThat(fence.get("status")).isEqualTo("fail");
    assertThat(result.get("blocking")).isEqualTo(true);
  }

  @Test
  void audit_text_passes_forbidden_symbol_for_url_only_content() throws Exception {
    Map<String, Object> result = postJson(
        "/api/articles/audit-text",
        Map.of("text", "访问 https://example.com/a\n![图](https://cdn.example.com/a.png)"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
    Map<String, Object> forbidden = items.stream()
        .filter(item -> "FORBIDDEN_SYMBOL".equals(item.get("code")))
        .findFirst()
        .orElseThrow();
    assertThat(forbidden.get("status")).isEqualTo("pass");
  }

  @Test
  void publishing_render_returns_html() throws Exception {
    Map<String, Object> result = postJson(
        "/api/publishing/render",
        Map.of("markdown", "# Hello\n\nWorld", "themeKey", "default"));
    assertThat(result.get("html")).asString().contains("Hello").contains("World");
    assertThat(result.get("htmlHash")).asString().isNotBlank();
    assertThat(result.get("themeKey")).isEqualTo("default");
  }

  @Test
  void hot_article_review_repair_revalidate_jobs_complete() throws Exception {
    String articleId = "article-" + UUID.randomUUID();
    String content = "## 重复\n\n短文\n\n## 重复";

    com.workbench.job.facade.dto.job.HotArticleReviewJobInput reviewInput =
        new com.workbench.job.facade.dto.job.HotArticleReviewJobInput();
    reviewInput.put("content", content);
    reviewInput.put("contentHash", "hash");
    reviewInput.put("sources", List.of());
    reviewInput.put("standardVersion", "hot-news-review-v1");
    JobView review = jobs.create(
        JobTypes.HOT_ARTICLE_REVIEW,
        "article",
        articleId,
        reviewInput);
    Map<String, Object> terminal = awaitTerminal(review.getId());
    assertThat(terminal.get("status")).isEqualTo("succeeded");
    assertThat(eventHistory(review.getId())).extracting(e -> e.get("step")).contains("review");
    @SuppressWarnings("unchecked")
    Map<String, Object> output = (Map<String, Object>) terminal.get("output");
    assertThat(output.get("blockingFailed")).isEqualTo(true);
    assertThat(output.get("status")).isEqualTo("failed");

    assertReviewJob(
        jobs.create(
            JobTypes.HOT_ARTICLE_REVIEW_REPAIR,
            "article",
            articleId,
            new com.workbench.job.facade.dto.job.HotArticleReviewRepairJobInput()),
        "repair");
    assertReviewJob(
        jobs.create(
            JobTypes.HOT_ARTICLE_REVIEW_REVALIDATE,
            "article",
            articleId,
            new com.workbench.job.facade.dto.job.HotArticleReviewRevalidateJobInput()),
        "revalidate");
  }

  private void assertReviewJob(JobView created, String expectedStep) throws Exception {
    Map<String, Object> terminal = awaitTerminal(created.getId());
    assertThat(terminal.get("status")).isEqualTo("succeeded");
    assertThat(eventHistory(created.getId())).extracting(e -> e.get("step")).contains(expectedStep);
  }
}
