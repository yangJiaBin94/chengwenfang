package com.chengwenfang.mode.hotnews.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TopicResearchPlannerTest {
  @Test
  void extract_subject_strips_portal_noise() {
    String title = "据IT之家报道：Reflection AI 发布开放权重模型 Beam | 每日AI午报 10月06日";
    assertThat(TopicResearchPlanner.extractSubject(title))
        .isEqualTo("Reflection AI 发布开放权重模型 Beam");
    assertThat(TopicResearchPlanner.extractShortQuery(TopicResearchPlanner.extractSubject(title)))
        .contains("Reflection AI")
        .contains("Beam");
  }

  @Test
  void build_queries_cover_official_and_analysis_angles() {
    List<String> queries = TopicResearchPlanner.buildQueries(
        "据IT之家报道：Reflection AI 发布开放权重模型 Beam | 每日AI午报 10月06日",
        "");
    assertThat(queries).isNotEmpty();
    assertThat(queries.stream().anyMatch(q -> q.contains("官网") || q.contains("official"))).isTrue();
    assertThat(queries.stream().anyMatch(q -> q.contains("GitHub"))).isTrue();
  }

  @Test
  void build_queries_from_ai_plan_keeps_seed_keywords() {
    List<String> queries = TopicResearchPlanner.buildQueriesFromPlan(
        "Apple Inc",
        List.of("Apple Inc", "Apple 公司 AI"),
        "苹果公司不是水果");
    assertThat(queries).contains("Apple Inc", "Apple 公司 AI");
    assertThat(queries.stream().noneMatch(q -> q.contains("水果"))).isTrue();
  }

  @Test
  void normalize_url_drops_tracking() {
    assertThat(TopicResearchPlanner.normalizeUrl(
        "https://Example.com/a/b/?utm_source=x&id=1#frag"))
        .isEqualTo("https://example.com/a/b?id=1");
  }

  @Test
  void material_dedupe_key_prefers_normalized_url() {
    assertThat(TopicResearchPlanner.materialDedupeKey(
            "https://Example.com/a/b/?utm_source=x&id=1#frag",
            "任意标题"))
        .isEqualTo("url:https://example.com/a/b?id=1");
  }

  @Test
  void material_dedupe_key_falls_back_to_title() {
    assertThat(TopicResearchPlanner.materialDedupeKey(null, "  Hello World  "))
        .isEqualTo("title:hello world");
    assertThat(TopicResearchPlanner.materialDedupeKey("", "Hello World"))
        .isEqualTo("title:hello world");
  }

  @Test
  void meaningful_material_dedupe_key_rejects_empty() {
    assertThat(TopicResearchPlanner.isMeaningfulMaterialDedupeKey(
            TopicResearchPlanner.materialDedupeKey(null, null)))
        .isFalse();
    assertThat(TopicResearchPlanner.isMeaningfulMaterialDedupeKey(
            TopicResearchPlanner.materialDedupeKey("https://a.com/x", "")))
        .isTrue();
  }
}
