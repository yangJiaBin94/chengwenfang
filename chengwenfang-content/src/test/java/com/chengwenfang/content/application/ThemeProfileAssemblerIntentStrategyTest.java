package com.chengwenfang.content.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.content.facade.constant.ThemeProfileKinds;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 热点主要意图与采集策略经画像列表项 JSON 往返不丢。
 */
class ThemeProfileAssemblerIntentStrategyTest {

  @Test
  void roundTrip_preservesConfirmedIntentAndUnstructuredStrategy() {
    Map<String, Object> naming = new LinkedHashMap<>();
    naming.put("name", "测试主题");
    naming.put("description", "说明");
    naming.put("code", "test_theme");

    Map<String, Object> intent = new LinkedHashMap<>();
    intent.put("summary", "盯可写事件");
    intent.put("material_wants", List.of("政策", "个股动态"));
    intent.put("material_avoids", List.of("百科介绍"));
    intent.put("success_looks_like", "近几天可核实");
    intent.put("confirmed", true);

    Map<String, Object> strategy = new LinkedHashMap<>();
    strategy.put("mode", "unstructured");
    strategy.put("rationale", "需先发现再深挖");
    strategy.put("user_override", false);

    Map<String, Object> radar = new LinkedHashMap<>();
    radar.put("search_queries", List.of("A股 热点"));
    radar.put("seed_channels", List.of("searxng"));

    Map<String, Object> briefing = new LinkedHashMap<>();
    briefing.put("schema_version", 3);
    briefing.put("step", 5);
    briefing.put("naming", naming);
    briefing.put("core_intent", intent);
    briefing.put("collect_strategy", strategy);
    briefing.put("radar", radar);

    ThemeProfileAssembler.AssembledProfile assembled =
        ThemeProfileAssembler.fromMap(
            "theme-1", ThemeProfileKinds.BRIEFING, briefing, LocalDateTime.now());
    Map<String, Object> restored =
        ThemeProfileAssembler.toBriefingMap(
            assembled.profile(), assembled.items(), assembled.rubric(), assembled.dimensions());

    @SuppressWarnings("unchecked")
    Map<String, Object> restoredIntent = (Map<String, Object>) restored.get("core_intent");
    @SuppressWarnings("unchecked")
    Map<String, Object> restoredStrategy = (Map<String, Object>) restored.get("collect_strategy");

    assertThat(restoredIntent).isNotNull();
    assertThat(restoredIntent.get("summary")).isEqualTo("盯可写事件");
    assertThat(restoredIntent.get("confirmed")).isEqualTo(true);
    assertThat(restoredIntent.get("material_wants")).isEqualTo(List.of("政策", "个股动态"));
    assertThat(restoredIntent.get("material_avoids")).isEqualTo(List.of("百科介绍"));
    assertThat(restoredIntent.get("success_looks_like")).isEqualTo("近几天可核实");

    assertThat(restoredStrategy).isNotNull();
    assertThat(restoredStrategy.get("mode")).isEqualTo("unstructured");
    assertThat(restoredStrategy.get("rationale")).isEqualTo("需先发现再深挖");
    assertThat(restoredStrategy.get("user_override")).isEqualTo(false);
  }
}
