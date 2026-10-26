package com.chengwenfang.app.application.generation.custom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.chengwenfang.ai.facade.AiTextFacade;
import com.chengwenfang.mode.hotnews.application.generation.hotnews.HotNewsGraphStateKeys;
import com.chengwenfang.mode.hotnews.facade.HotNewsFacade;
import com.chengwenfang.skill.facade.SkillFacade;
import com.chengwenfang.skill.facade.constant.CustomAiNodeActions;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** {@link CustomAiNodeHandler#applyActions} 边界单测。 */
class CustomAiNodeHandlerApplyTest {
  private final CustomAiNodeHandler handler = new CustomAiNodeHandler(
      mock(AiTextFacade.class),
      mock(SkillFacade.class),
      new CustomAiActionPlanParser(),
      mock(HotNewsFacade.class));

  @Test
  void write_draft_requires_existing_draft() {
    CustomAiNodeHandler.ContextBundle context = new CustomAiNodeHandler.ContextBundle(
        false, "", false, "", false, "", false, "", false, "");
    assertThatThrownBy(() -> handler.applyActions(
            List.of(CustomAiParsedAction.builder()
                .op(CustomAiNodeActions.WRITE_DRAFT)
                .content("x")
                .build()),
            Set.of(),
            context))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("尚未生成原稿");
  }

  @Test
  void write_draft_succeeds_when_available() {
    CustomAiNodeHandler.ContextBundle context = new CustomAiNodeHandler.ContextBundle(
        false, "", false, "", false, "", true, "old", false, "");
    Map<String, Object> delta = handler.applyActions(
        List.of(CustomAiParsedAction.builder()
            .op(CustomAiNodeActions.WRITE_DRAFT)
            .content("new draft")
            .build()),
        Set.of(),
        context);
    assertThat(delta.get(HotNewsGraphStateKeys.DRAFT)).isEqualTo("new draft");
  }

  @Test
  void read_skill_must_be_bound() {
    CustomAiNodeHandler.ContextBundle context = new CustomAiNodeHandler.ContextBundle(
        false, "", false, "", false, "", true, "d", false, "");
    assertThatThrownBy(() -> handler.applyActions(
            List.of(CustomAiParsedAction.builder()
                .op(CustomAiNodeActions.READ_SKILL)
                .skillId("missing")
                .build()),
            Set.of("bound-1"),
            context))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("read_skill");
  }
}
