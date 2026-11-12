package com.chengwenfang.prompt.facade;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.prompt.facade.constant.CoursePromptPackNames;
import com.chengwenfang.prompt.facade.constant.CourseWorkflowKeys;
import com.chengwenfang.prompt.facade.constant.PromptPackSceneKeys;
import com.chengwenfang.prompt.facade.dto.view.PromptPackView;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link PromptPackBindingIds} 新旧 提示词包 名别名解析。 */
class PromptPackBindingIdsTest {

  @Test
  void promptPackNameMatches_acceptsLegacyRulesName() {
    assertThat(PromptPackBindingIds.promptPackNameMatches(
            CoursePromptPackNames.CHENGWENFANG_RULES, CoursePromptPackNames.LEGACY_RULES))
        .isTrue();
    assertThat(PromptPackBindingIds.promptPackNameMatches(
            CoursePromptPackNames.CHENGWENFANG_RULES, CoursePromptPackNames.CHENGWENFANG_RULES))
        .isTrue();
    assertThat(PromptPackBindingIds.promptPackNameMatches(
            CoursePromptPackNames.COURSE_REVIEW, CoursePromptPackNames.LEGACY_RULES))
        .isFalse();
  }

  @Test
  void pickCoursePromptPackId_resolvesLegacyRulesName() {
    PromptPackView legacy = PromptPackView.builder()
        .id("prompt-pack-legacy")
        .name(CoursePromptPackNames.LEGACY_RULES)
        .scene(PromptPackSceneKeys.GLOBAL)
        .scope("system")
        .build();
    String id = PromptPackBindingIds.pickCoursePromptPackId(
        List.of(legacy), CoursePromptPackNames.CHENGWENFANG_RULES);
    assertThat(id).isEqualTo("prompt-pack-legacy");
  }

  @Test
  void resolveForWorkflowScene_resolvesLegacyRulesName() {
    PromptPackView legacy = PromptPackView.builder()
        .id("prompt-pack-legacy")
        .name(CoursePromptPackNames.LEGACY_RULES)
        .scene(CourseWorkflowKeys.SCENE)
        .build();
    String id = PromptPackBindingIds.resolveForWorkflowScene(
        List.of(legacy), CoursePromptPackNames.CHENGWENFANG_RULES, CourseWorkflowKeys.SCENE);
    assertThat(id).isEqualTo("prompt-pack-legacy");
  }
}
