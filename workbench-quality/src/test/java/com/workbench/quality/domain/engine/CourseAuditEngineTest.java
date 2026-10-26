package com.workbench.quality.domain.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CourseAuditEngineTest {
  private final CourseAuditEngine engine = new CourseAuditEngine();

  @Test
  void urls_and_markdown_targets_do_not_trigger_forbidden_slashes() {
    String article = "访问 https://example.com/a -> 这里。\n[文档](https://example.com/docs)\n`https://api.test/v1`";
    // Visible arrow outside URL/link text should fail.
    CourseAuditEngine.AuditResult withVisibleArrow = engine.run(ctx(article));
    assertThat(byCode(withVisibleArrow, "FORBIDDEN_SYMBOL").status()).isEqualTo("fail");
    assertThat(byCode(withVisibleArrow, "FORBIDDEN_SYMBOL").detail()).contains("->");
  }

  @Test
  void technical_urls_alone_pass_forbidden_symbol_rule() {
    String article = "访问 https://example.com/a。\n![图片](https://cdn.example.com/a.png)\n<https://docs.example.com>";
    assertThat(byCode(engine.run(ctx(article)), "FORBIDDEN_SYMBOL").status()).isEqualTo("pass");
  }

  @Test
  void visible_forbidden_symbols_are_detected_and_located() {
    String article = "## 第四层：行动\n错误 // 路径 -> 结果 —— 需要修改";
    CourseAuditEngine.AuditCheck result = byCode(engine.run(ctx(article)), "FORBIDDEN_SYMBOL");
    assertThat(result.status()).isEqualTo("fail");
    assertThat(result.location()).isEqualTo("第四层：行动（第 2 行）");
    assertThat(result.fixableByTextAi()).isTrue();
    assertThat(result.blocking()).isTrue();
  }

  @Test
  void text_and_asset_issues_are_classified_separately() {
    CourseAuditEngine.AuditResult report = engine.run(ctx("普通正文"));
    assertThat(byCode(report, "OPENING_SUMMARY_COMPLETE").fixableByTextAi()).isTrue();
    assertThat(byCode(report, "IMAGE_ASSETS_PRESENT").fixableByTextAi()).isFalse();
    assertThat(byCode(report, "IMAGE_METADATA_COMPLETE").fixableByTextAi()).isFalse();
    assertThat(byCode(report, "ARTICLE_IMAGE_REFERENCE").fixableByTextAi()).isFalse();
  }

  @Test
  void image_reference_is_text_fixable_only_when_assets_exist() {
    List<Map<String, Object>> assets = List.of(Map.of(
        "name", "cover.png",
        "path", "/assets/cover.png",
        "articleId", "article-1",
        "articlePath", "article.md"));
    CourseAuditEngine.AuditResult report = engine.run(new CourseAuditEngine.AuditContext(
        "普通正文", "", assets, List.of(), true, null));
    CourseAuditEngine.AuditCheck imageReference = byCode(report, "ARTICLE_IMAGE_REFERENCE");
    assertThat(imageReference.status()).isEqualTo("warn");
    assertThat(imageReference.fixableByTextAi()).isTrue();
  }

  @Test
  void database_article_does_not_require_asset_article_path() {
    List<Map<String, Object>> assets = List.of(Map.of(
        "name", "cover.png",
        "path", "/api/images/1",
        "articleId", "article-1"));
    CourseAuditEngine.AuditResult report = engine.run(new CourseAuditEngine.AuditContext(
        "普通正文", "", assets, List.of(), true, null));
    assertThat(byCode(report, "IMAGE_METADATA_COMPLETE").status()).isEqualTo("pass");
  }

  @Test
  void file_backed_article_requires_asset_article_path() {
    List<Map<String, Object>> assets = List.of(Map.of(
        "name", "cover.png",
        "path", "/api/images/1",
        "articleId", "article-1"));
    CourseAuditEngine.AuditResult report = engine.run(new CourseAuditEngine.AuditContext(
        "普通正文", "", assets, List.of(), true, "content/article.md"));
    CourseAuditEngine.AuditCheck metadata = byCode(report, "IMAGE_METADATA_COMPLETE");
    assertThat(metadata.status()).isEqualTo("warn");
    assertThat(metadata.detail()).contains("文章文件路径");
  }

  private static CourseAuditEngine.AuditContext ctx(String article) {
    return new CourseAuditEngine.AuditContext(article, "", List.of(), List.of(), true, null);
  }

  private static CourseAuditEngine.AuditCheck byCode(CourseAuditEngine.AuditResult result, String code) {
    return result.items().stream()
        .filter(item -> code.equals(item.code()))
        .findFirst()
        .orElseThrow();
  }
}
