package com.chengwenfang.prompt.facade.enums;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link HotNewsRadarCollectNodeType} 主流程节点序。 */
class HotNewsRadarCollectNodeTypeTest {

  @Test
  void builtinOrder_placesWebSearchBetweenFilterAndPersist() {
    List<HotNewsRadarCollectNodeType> builtins =
        new ArrayList<>(HotNewsRadarCollectNodeType.builtinSet());
    int filter = builtins.indexOf(HotNewsRadarCollectNodeType.CANDIDATE_FILTER);
    int webSearch = builtins.indexOf(HotNewsRadarCollectNodeType.AI_WEB_SEARCH_TOPICS);
    int persist = builtins.indexOf(HotNewsRadarCollectNodeType.PERSIST_CLUSTER);
    int aiSeed = builtins.indexOf(HotNewsRadarCollectNodeType.AI_THEME_SEED);
    int score = builtins.indexOf(HotNewsRadarCollectNodeType.SCORE_TOP10);
    assertThat(filter).isGreaterThanOrEqualTo(0);
    assertThat(webSearch).isEqualTo(filter + 1);
    assertThat(persist).isEqualTo(webSearch + 1);
    assertThat(aiSeed).isEqualTo(persist + 1);
    assertThat(score).isEqualTo(aiSeed + 1);
  }

  @Test
  void from_resolvesAiThemeSeedAndWebSearchTypeKeys() {
    assertThat(HotNewsRadarCollectNodeType.from("radar_ai_theme_seed"))
        .contains(HotNewsRadarCollectNodeType.AI_THEME_SEED);
    assertThat(HotNewsRadarCollectNodeType.from("radar_ai_web_search_topics"))
        .contains(HotNewsRadarCollectNodeType.AI_WEB_SEARCH_TOPICS);
  }
}
