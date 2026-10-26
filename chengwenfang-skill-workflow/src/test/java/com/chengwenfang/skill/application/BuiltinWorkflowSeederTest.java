package com.chengwenfang.skill.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.chengwenfang.skill.facade.constant.CourseSkillNames;
import com.chengwenfang.skill.facade.constant.CourseWorkflowKeys;
import com.chengwenfang.skill.facade.constant.HotNewsWorkflowKeys;
import com.chengwenfang.skill.facade.dto.view.SkillView;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 课程 Skill 同名解析：排除热点 scene。
 */
class BuiltinWorkflowSeederTest {

  @Test
  void pickCourseSkillId_prefersCourseSceneOverHotNewsDuplicate() {
    List<SkillView> skills = List.of(
        SkillView.builder()
            .id("hot-img")
            .name(CourseSkillNames.IMAGE_GENERATION)
            .scene(HotNewsWorkflowKeys.SCENE)
            .scope("scene")
            .build(),
        SkillView.builder()
            .id("course-img")
            .name(CourseSkillNames.IMAGE_GENERATION)
            .scene(CourseWorkflowKeys.SCENE)
            .scope("scene")
            .build());
    assertEquals(
        "course-img",
        BuiltinWorkflowSeeder.pickCourseSkillId(skills, CourseSkillNames.IMAGE_GENERATION));
  }

  @Test
  void pickCourseSkillId_fallsBackToGlobalSystemNotHotNews() {
    List<SkillView> skills = List.of(
        SkillView.builder()
            .id("hot-img")
            .name(CourseSkillNames.IMAGE_GENERATION)
            .scene(HotNewsWorkflowKeys.SCENE)
            .scope("scene")
            .build(),
        SkillView.builder()
            .id("sys-img")
            .name(CourseSkillNames.IMAGE_GENERATION)
            .scene("global")
            .scope("system")
            .build());
    assertEquals(
        "sys-img",
        BuiltinWorkflowSeeder.pickCourseSkillId(skills, CourseSkillNames.IMAGE_GENERATION));
  }

  @Test
  void pickCourseSkillId_returnsNullWhenOnlyHotNewsExists() {
    List<SkillView> skills = List.of(
        SkillView.builder()
            .id("hot-img")
            .name(CourseSkillNames.IMAGE_GENERATION)
            .scene(HotNewsWorkflowKeys.SCENE)
            .scope("scene")
            .build());
    assertNull(BuiltinWorkflowSeeder.pickCourseSkillId(skills, CourseSkillNames.IMAGE_GENERATION));
  }
}
