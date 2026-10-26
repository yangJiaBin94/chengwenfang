package com.chengwenfang.skill.facade;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.skill.facade.constant.CourseSkillNames;
import com.chengwenfang.skill.facade.constant.CourseWorkflowKeys;
import com.chengwenfang.skill.facade.constant.SkillSceneKeys;
import com.chengwenfang.skill.facade.dto.view.SkillView;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link SkillBindingIds} 新旧 Skill 名别名解析。 */
class SkillBindingIdsTest {

  @Test
  void skillNameMatches_acceptsLegacyRulesName() {
    assertThat(SkillBindingIds.skillNameMatches(
            CourseSkillNames.CHENGWENFANG_RULES, CourseSkillNames.LEGACY_RULES))
        .isTrue();
    assertThat(SkillBindingIds.skillNameMatches(
            CourseSkillNames.CHENGWENFANG_RULES, CourseSkillNames.CHENGWENFANG_RULES))
        .isTrue();
    assertThat(SkillBindingIds.skillNameMatches(
            CourseSkillNames.COURSE_REVIEW, CourseSkillNames.LEGACY_RULES))
        .isFalse();
  }

  @Test
  void pickCourseSkillId_resolvesLegacyRulesName() {
    SkillView legacy = SkillView.builder()
        .id("skill-legacy")
        .name(CourseSkillNames.LEGACY_RULES)
        .scene(SkillSceneKeys.GLOBAL)
        .scope("system")
        .build();
    String id = SkillBindingIds.pickCourseSkillId(
        List.of(legacy), CourseSkillNames.CHENGWENFANG_RULES);
    assertThat(id).isEqualTo("skill-legacy");
  }

  @Test
  void resolveForWorkflowScene_resolvesLegacyRulesName() {
    SkillView legacy = SkillView.builder()
        .id("skill-legacy")
        .name(CourseSkillNames.LEGACY_RULES)
        .scene(CourseWorkflowKeys.SCENE)
        .build();
    String id = SkillBindingIds.resolveForWorkflowScene(
        List.of(legacy), CourseSkillNames.CHENGWENFANG_RULES, CourseWorkflowKeys.SCENE);
    assertThat(id).isEqualTo("skill-legacy");
  }
}
