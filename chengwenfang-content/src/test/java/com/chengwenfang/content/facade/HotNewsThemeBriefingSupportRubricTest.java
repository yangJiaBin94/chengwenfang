package com.chengwenfang.content.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chengwenfang.common.BusinessException;
import com.chengwenfang.content.facade.dto.view.ScoringRubricDimensionView;
import com.chengwenfang.content.facade.dto.view.ScoringRubricView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 热点主题 commit 对评分量表的门禁。 */
class HotNewsThemeBriefingSupportRubricTest {

  @Test
  void assertReadyToCommit_requiresValidRubric() {
    Map<String, Object> briefing = baseBriefing();
    assertThatThrownBy(() -> HotNewsThemeBriefingSupport.assertReadyToCommit(briefing))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("评分量表");
  }

  @Test
  void assertReadyToCommit_acceptsValidRubricAndBuildDefaults() {
    Map<String, Object> briefing = baseBriefing();
    ScoringRubricSupport.writeInto(
        briefing,
        ScoringRubricView.builder()
            .version(1)
            .dimensions(List.of(
                dim("theme_fit", "主题契合", "契合度", 40),
                dim("audience_payoff", "读者收益", "收益", 35),
                dim("angle_potential", "成文切入", "切入", 25)))
            .build());
    HotNewsThemeBriefingSupport.assertReadyToCommit(briefing);
    Map<String, Object> defaults = HotNewsThemeBriefingSupport.buildDefaults(briefing);
    assertThat(ScoringRubricSupport.readFrom(defaults)).isNotNull();
    assertThat(ScoringRubricSupport.readFrom(defaults).getDimensions()).hasSize(3);
  }

  private static Map<String, Object> baseBriefing() {
    Map<String, Object> naming = new LinkedHashMap<>();
    naming.put("name", "测试主题");
    Map<String, Object> radar = new LinkedHashMap<>();
    radar.put("search_queries", List.of("AI 资讯"));
    radar.put("seed_channels", List.of("searxng"));
    Map<String, Object> briefing = new LinkedHashMap<>();
    briefing.put("naming", naming);
    briefing.put("radar", radar);
    return briefing;
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
