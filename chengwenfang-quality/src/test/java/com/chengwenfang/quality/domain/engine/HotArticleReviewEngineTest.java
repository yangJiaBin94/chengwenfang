package com.chengwenfang.quality.domain.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chengwenfang.common.BusinessException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class HotArticleReviewEngineTest {
  private final HotArticleReviewEngine engine = new HotArticleReviewEngine();

  @Test
  void parse_markdown_preserves_heading_positions() {
    Map<String, Object> parsed = engine.parseMarkdown("# 标题\n\n开场\n\n## 事件\n\n正文");
    assertThat(parsed.get("titleCount")).isEqualTo(1L);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> sections = (List<Map<String, Object>>) parsed.get("sections");
    assertThat(sections.get(0).get("heading")).isEqualTo("事件");
    assertThat(sections.get(0).get("content")).isEqualTo("正文");
  }

  @Test
  void rule_review_reports_structure_and_source_issues() {
    List<Map<String, Object>> results = engine.runRuleReview(Map.of(
        "content", "## 重复\n\n短文\n\n## 重复",
        "sources", List.of(),
        "contentHash", "x",
        "standardVersion", "hot-news-review-v1"));
    Set<String> failed = results.stream()
        .filter(item -> "failed".equals(item.get("status")))
        .map(item -> String.valueOf(item.get("code")))
        .collect(Collectors.toSet());
    assertThat(failed).contains(
        "ARTICLE_TITLE",
        "ARTICLE_SECTIONS",
        "ARTICLE_MIN_LENGTH",
        "SOURCE_PRESENT");
    assertThat(failed).doesNotContain(
        "SOURCE_APPENDIX_REQUIRED",
        "SOURCE_APPENDIX_AT_END",
        "SOURCE_REFERENCE_COMPLETE");
    assertThat(engine.catalog()).hasSizeLessThanOrEqualTo(20);
  }

  @Test
  void ai_result_rejects_unknown_catalog_code() {
    assertThatThrownBy(() -> engine.validateAiResults(
            Map.of("items", List.of(Map.of("code", "UNKNOWN", "status", "failed"))), null, null))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getCode())
        .isEqualTo("HOT_REVIEW_AI_INVALID");
  }

  @Test
  void ai_result_rejects_unverifiable_location() {
    assertThatThrownBy(() -> engine.validateAiResults(
            Map.of("items", List.of(Map.of(
                "code", "FACT_SUPPORT",
                "status", "failed",
                "locations", List.of(Map.of("quote", "错", "start", 0, "end", 1))))),
            "正确",
            List.of()))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getCode())
        .isEqualTo("HOT_REVIEW_AI_INVALID");
  }

  @Test
  void ai_result_rejects_source_outside_snapshot() {
    assertThatThrownBy(() -> engine.validateAiResults(
            Map.of("items", List.of(Map.of(
                "code", "FACT_SUPPORT",
                "status", "failed",
                "evidence", List.of(Map.of("sourceUrl", "https://bad.example"))))),
            "文章",
            List.of(Map.of("url", "https://good.example"))))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getCode())
        .isEqualTo("HOT_REVIEW_AI_INVALID");
  }

  @Test
  void failed_review_cannot_be_approved() {
    assertThat(engine.canApprove("failed")).isFalse();
    assertThatThrownBy(() -> engine.assertCanApprove("failed"))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getCode())
        .isEqualTo("HOT_ARTICLE_REVIEW_BLOCKED");
    assertThat(engine.canApprove("passed")).isTrue();
  }
}
