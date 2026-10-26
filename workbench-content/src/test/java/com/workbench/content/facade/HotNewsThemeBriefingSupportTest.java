package com.workbench.content.facade;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.workbench.common.BusinessException;
import com.workbench.content.facade.constant.HotNewsThemeSeedChannelKeys;
import com.workbench.content.facade.dto.view.ScoringRubricDimensionView;
import com.workbench.content.facade.dto.view.ScoringRubricView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 热点主题问卷校验单测。 */
class HotNewsThemeBriefingSupportTest {

  @Test
  void assertReadyToCommit_rejectsEmptyQueries() {
    Map<String, Object> briefing = validBriefing();
    @SuppressWarnings("unchecked")
    Map<String, Object> radar = (Map<String, Object>) briefing.get("radar");
    radar.put("search_queries", List.of());
    BusinessException exception =
        assertThrows(BusinessException.class, () -> HotNewsThemeBriefingSupport.assertReadyToCommit(briefing));
    assertTrue(exception.getMessage().contains("短关键词"));
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
    putValidRubric(briefing);
    assertDoesNotThrow(() -> HotNewsThemeBriefingSupport.assertReadyToCommit(briefing));
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
    briefing.put("schema_version", 1);
    briefing.put("naming", naming);
    briefing.put("audience", audience);
    briefing.put("value_filters", valueFilters);
    briefing.put("scope", scope);
    briefing.put("radar", radar);
    briefing.put("expression", expression);
    putValidRubric(briefing);
    return briefing;
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
