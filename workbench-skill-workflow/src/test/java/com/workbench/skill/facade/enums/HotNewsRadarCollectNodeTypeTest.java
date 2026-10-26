package com.workbench.skill.facade.enums;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link HotNewsRadarCollectNodeType} 主流程节点序。 */
class HotNewsRadarCollectNodeTypeTest {

  @Test
  void builtinOrder_placesAiThemeSeedBetweenPersistAndScore() {
    List<HotNewsRadarCollectNodeType> builtins =
        new ArrayList<>(HotNewsRadarCollectNodeType.builtinSet());
    int persist = builtins.indexOf(HotNewsRadarCollectNodeType.PERSIST_CLUSTER);
    int aiSeed = builtins.indexOf(HotNewsRadarCollectNodeType.AI_THEME_SEED);
    int score = builtins.indexOf(HotNewsRadarCollectNodeType.SCORE_TOP10);
    assertThat(persist).isGreaterThanOrEqualTo(0);
    assertThat(aiSeed).isEqualTo(persist + 1);
    assertThat(score).isEqualTo(aiSeed + 1);
  }

  @Test
  void from_resolvesAiThemeSeedTypeKey() {
    assertThat(HotNewsRadarCollectNodeType.from("radar_ai_theme_seed"))
        .contains(HotNewsRadarCollectNodeType.AI_THEME_SEED);
  }
}
