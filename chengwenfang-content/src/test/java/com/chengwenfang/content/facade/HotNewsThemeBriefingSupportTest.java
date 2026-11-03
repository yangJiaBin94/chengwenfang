package com.chengwenfang.content.facade;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chengwenfang.common.BusinessException;
import com.chengwenfang.content.facade.constant.HotNewsThemeSeedChannelKeys;
import com.chengwenfang.content.facade.dto.view.ScoringRubricDimensionView;
import com.chengwenfang.content.facade.dto.view.ScoringRubricView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 热点主题问卷校验单测。 */
class HotNewsThemeBriefingSupportTest {

  @Test
  void assertReadyToCommit_rejectsEmptyQueriesForStructured() {
    Map<String, Object> briefing = validBriefing();
    @SuppressWarnings("unchecked")
    Map<String, Object> radar = (Map<String, Object>) briefing.get("radar");
    radar.put("search_queries", List.of());
    BusinessException exception =
        assertThrows(BusinessException.class, () -> HotNewsThemeBriefingSupport.assertReadyToCommit(briefing));
    assertTrue(exception.getMessage().contains("短关键词"));
  }

  @Test
  void assertReadyToCommit_acceptsUnstructuredWithoutQueries() {
    Map<String, Object> briefing = validBriefing();
    @SuppressWarnings("unchecked")
    Map<String, Object> radar = (Map<String, Object>) briefing.get("radar");
    radar.put("search_queries", List.of());
    Map<String, Object> strategy = new LinkedHashMap<>();
    strategy.put("mode", "unstructured");
    strategy.put("rationale", "需要先发现再深挖");
    briefing.put("collect_strategy", strategy);
    assertDoesNotThrow(() -> HotNewsThemeBriefingSupport.assertReadyToCommit(briefing));
  }

  @Test
  void assertReadyToCommit_acceptsMinimalPartitionBriefing() {
    Map<String, Object> naming = new LinkedHashMap<>();
    naming.put("name", "分区主题");
    Map<String, Object> radar = new LinkedHashMap<>();
    radar.put("search_queries", List.of("AI 热点"));
    radar.put("seed_channels", List.of("searxng"));
    Map<String, Object> briefing = new LinkedHashMap<>();
    briefing.put("naming", naming);
    briefing.put("radar", radar);
    putConfirmedIntent(briefing);
    putStructuredStrategy(briefing);
    putValidRubric(briefing);
    assertDoesNotThrow(() -> HotNewsThemeBriefingSupport.assertReadyToCommit(briefing));
  }

  @Test
  void themeContextPrompt_includesCoreIntent() {
    String prompt = HotNewsThemeBriefingSupport.themeContextPrompt(validBriefing(), "测试主题");
    assertTrue(prompt.contains("主要意图"));
    assertTrue(prompt.contains("盯测试领域最新可写事件"));
  }

  @Test
  void seedChannelTypes_defaultsToSearxng() {
    Map<String, Object> briefing = validBriefing();
    @SuppressWarnings("unchecked")
    Map<String, Object> radar = (Map<String, Object>) briefing.get("radar");
    radar.remove("seed_channels");
    assertEquals(List.of(HotNewsThemeSeedChannelKeys.SEARXNG), HotNewsThemeBriefingSupport.seedChannelTypes(briefing));
  }

  @Test
  void themeContextPrompt_includesReaders() {
    String prompt = HotNewsThemeBriefingSupport.themeContextPrompt(validBriefing(), "测试主题");
    assertTrue(prompt.contains("测试主题"));
    assertTrue(prompt.contains("developer"));
  }

  private static Map<String, Object> validBriefing() {
    Map<String, Object> naming = new LinkedHashMap<>();
    naming.put("name", "测试主题");
    naming.put("description", "盯测试领域");
    naming.put("code", "test_theme");
    naming.put("domain", "custom");

    Map<String, Object> audience = new LinkedHashMap<>();
    audience.put("readers", List.of("developer"));
    audience.put("content_stance", "deep_analysis");

    Map<String, Object> valueFilters = new LinkedHashMap<>();
    valueFilters.put("worth_writing", List.of("multi_source"));

    Map<String, Object> scope = new LinkedHashMap<>();
    scope.put("must_watch", List.of("Foo"));
    scope.put("out_of_scope", "Bar");
    scope.put("locale", "zh");

    Map<String, Object> radar = new LinkedHashMap<>();
    radar.put("search_queries", List.of("Foo 发布"));
    radar.put("github_query", "");
    radar.put("seed_channels", List.of("searxng"));

    Map<String, Object> expression = new LinkedHashMap<>();
    expression.put("tone", "chatty");
    expression.put("boundaries", "");

    Map<String, Object> briefing = new LinkedHashMap<>();
    briefing.put("schema_version", 3);
    briefing.put("naming", naming);
    briefing.put("audience", audience);
    briefing.put("value_filters", valueFilters);
    briefing.put("scope", scope);
    briefing.put("radar", radar);
    briefing.put("expression", expression);
    putConfirmedIntent(briefing);
    putStructuredStrategy(briefing);
    putValidRubric(briefing);
    return briefing;
  }

  /** 写入已确认主要意图。 */
  private static void putConfirmedIntent(Map<String, Object> briefing) {
    Map<String, Object> intent = new LinkedHashMap<>();
    intent.put("summary", "盯测试领域最新可写事件");
    intent.put("material_wants", List.of("产品发布", "开源动态"));
    intent.put("material_avoids", List.of("机构百科介绍"));
    intent.put("success_looks_like", "近几天可核实事件且有读者行动点");
    intent.put("confirmed", true);
    briefing.put("core_intent", intent);
  }

  /** 写入结构化采集策略。 */
  private static void putStructuredStrategy(Map<String, Object> briefing) {
    Map<String, Object> strategy = new LinkedHashMap<>();
    strategy.put("mode", "structured");
    strategy.put("rationale", "固定词可覆盖");
    strategy.put("user_override", false);
    briefing.put("collect_strategy", strategy);
  }

  /** 写入启用门禁所需的合法评分量表。 */
  private static void putValidRubric(Map<String, Object> briefing) {
    ScoringRubricSupport.writeInto(
        briefing,
        ScoringRubricView.builder()
            .version(1)
            .dimensions(List.of(
                dim("theme_fit", "主题契合", "契合度", 40),
                dim("audience_payoff", "读者收益", "收益", 35),
                dim("angle_potential", "成文切入", "切入", 25)))
            .build());
  }

  private static ScoringRubricDimensionView dim(
      String key, String label, String description, int weight) {
    return ScoringRubricDimensionView.builder()
        .key(key)
        .label(label)
        .description(description)
        .weight(weight)
        .build();
  }
}
