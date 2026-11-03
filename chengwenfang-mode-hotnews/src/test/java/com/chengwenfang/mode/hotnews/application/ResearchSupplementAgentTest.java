package com.chengwenfang.mode.hotnews.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.mode.hotnews.application.research.ResearchAgentAction;
import com.chengwenfang.mode.hotnews.application.research.ResearchAgentTurnResponse;
import com.chengwenfang.mode.hotnews.facade.enums.ResearchAgentActionType;
import org.junit.jupiter.api.Test;

/** {@link ResearchSupplementAgent} JSON 解析。 */
class ResearchSupplementAgentTest {

  @Test
  void parse_turn_with_search_fetch_finish() {
    ResearchAgentTurnResponse turn = ResearchSupplementAgent.parseTurn("""
        {"actions":[
          {"type":"search","queries":["皇冠新材 上市","皇冠新材 公告"]},
          {"type":"fetch","urls":["https://example.com/a"]},
          {"type":"finish","items":[
            {"url":"https://example.com/a","title":"公告","reason":"发行价"}
          ]}
        ]}
        """);
    assertThat(turn).isNotNull();
    assertThat(turn.getActions()).hasSize(3);
    ResearchAgentAction search = turn.getActions().get(0);
    assertThat(ResearchAgentActionType.from(search.getType())).isEqualTo(ResearchAgentActionType.SEARCH);
    assertThat(search.getQueries()).containsExactly("皇冠新材 上市", "皇冠新材 公告");
    assertThat(turn.getActions().get(1).getUrls()).containsExactly("https://example.com/a");
    assertThat(turn.getActions().get(2).getItems()).hasSize(1);
    assertThat(turn.getActions().get(2).getItems().get(0).getUrl()).isEqualTo("https://example.com/a");
  }

  @Test
  void parse_turn_ignores_markdown_fence_noise() {
    ResearchAgentTurnResponse turn = ResearchSupplementAgent.parseTurn("""
        如下：
        ```json
        {"actions":[{"type":"search","queries":["Apple Inc 财报"]}]}
        ```
        """);
    assertThat(turn).isNotNull();
    assertThat(turn.getActions()).hasSize(1);
    assertThat(turn.getActions().get(0).getQueries()).containsExactly("Apple Inc 财报");
  }

  @Test
  void parse_turn_rejects_empty_actions() {
    assertThat(ResearchSupplementAgent.parseTurn("{\"actions\":[]}")).isNull();
    assertThat(ResearchSupplementAgent.parseTurn("not json")).isNull();
  }
}
