package com.chengwenfang.app.application.generation.custom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chengwenfang.skill.facade.constant.CustomAiNodeActions;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link CustomAiActionPlanParser} 白名单解析单测。 */
class CustomAiActionPlanParserTest {
  private final CustomAiActionPlanParser parser = new CustomAiActionPlanParser();

  @Test
  void parses_whitelist_actions() {
    List<CustomAiParsedAction> actions = parser.parse(
        """
        {"actions":[
          {"op":"read_draft"},
          {"op":"write_draft","content":"# 新稿"}
        ]}
        """);
    assertThat(actions).hasSize(2);
    assertThat(actions.get(0).getOp()).isEqualTo(CustomAiNodeActions.READ_DRAFT);
    assertThat(actions.get(1).getOp()).isEqualTo(CustomAiNodeActions.WRITE_DRAFT);
    assertThat(actions.get(1).getContent()).isEqualTo("# 新稿");
  }

  @Test
  void rejects_unknown_op() {
    assertThatThrownBy(() -> parser.parse("{\"actions\":[{\"op\":\"delete_database\"}]}"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("越界动作");
  }

  @Test
  void rejects_invalid_json() {
    assertThatThrownBy(() -> parser.parse("not-json"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("合法 JSON");
  }
}
