package com.chengwenfang.mode.hotnews.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** {@link HotNewsDraftOptimizeCritiqueService} JSON 解析。 */
class HotNewsDraftOptimizeCritiqueServiceTest {

  @Test
  void parse_reads_score_dimensions_and_advice() {
    var view = HotNewsDraftOptimizeCritiqueService.parse("a1", """
        {"overallScore":72,
         "dimensionScores":{"narrative":60,"tone":80,"aiFlavor":55,"setupCompleteness":50,"coverage":70,"evidence":35},
         "issues":[{"dimension":"evidence","evidence":"三张截图","suggestion":"补上财联社链接"}],
         "editableAdvice":"1. 补来源链接\\n2. 去掉排比"}
        """);
    assertThat(view.getArticleId()).isEqualTo("a1");
    assertThat(view.getOverallScore()).isEqualTo(72);
    assertThat(view.getDimensionScores()).containsEntry("narrative", 60);
    assertThat(view.getDimensionScores()).containsEntry("evidence", 35);
    assertThat(view.getIssues()).hasSize(1);
    assertThat(view.getIssues().get(0).getDimension()).isEqualTo("evidence");
    assertThat(view.getIssues().get(0).getEvidence()).contains("截图");
    assertThat(view.getEditableAdvice()).contains("链接");
  }

  @Test
  void buildUserPrompt_includes_reference_urls() {
    var refs = java.util.List.of(
        com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsResearchItemView.builder()
            .title("财联社肉签")
            .url("https://example.com/a")
            .summary("盘中最高150.58")
            .build());
    String prompt = HotNewsDraftOptimizeCritiqueService.buildUserPrompt(
        "# 标题\n正文", "", null, refs);
    assertThat(prompt).contains("https://example.com/a");
    assertThat(prompt).contains("财联社肉签");
    assertThat(prompt).contains("原稿 Markdown");
  }
}
