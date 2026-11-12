package com.chengwenfang.prompt.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.chengwenfang.prompt.facade.constant.CoursePromptPackNames;
import com.chengwenfang.prompt.facade.constant.CourseWorkflowKeys;
import com.chengwenfang.prompt.facade.constant.HotNewsWorkflowKeys;
import com.chengwenfang.prompt.facade.dto.view.PromptPackView;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 课程 提示词包 同名解析：排除热点 scene。
 */
class BuiltinWorkflowSeederTest {

  @Test
  void pickCoursePromptPackId_prefersCourseSceneOverHotNewsDuplicate() {
    List<PromptPackView> promptPacks = List.of(
        PromptPackView.builder()
            .id("hot-img")
            .name(CoursePromptPackNames.IMAGE_GENERATION)
            .scene(HotNewsWorkflowKeys.SCENE)
            .scope("scene")
            .build(),
        PromptPackView.builder()
            .id("course-img")
            .name(CoursePromptPackNames.IMAGE_GENERATION)
            .scene(CourseWorkflowKeys.SCENE)
            .scope("scene")
            .build());
    assertEquals(
        "course-img",
        BuiltinWorkflowSeeder.pickCoursePromptPackId(promptPacks, CoursePromptPackNames.IMAGE_GENERATION));
  }

  @Test
  void pickCoursePromptPackId_fallsBackToGlobalSystemNotHotNews() {
    List<PromptPackView> promptPacks = List.of(
        PromptPackView.builder()
            .id("hot-img")
            .name(CoursePromptPackNames.IMAGE_GENERATION)
            .scene(HotNewsWorkflowKeys.SCENE)
            .scope("scene")
            .build(),
        PromptPackView.builder()
            .id("sys-img")
            .name(CoursePromptPackNames.IMAGE_GENERATION)
            .scene("global")
            .scope("system")
            .build());
    assertEquals(
        "sys-img",
        BuiltinWorkflowSeeder.pickCoursePromptPackId(promptPacks, CoursePromptPackNames.IMAGE_GENERATION));
  }

  @Test
  void pickCoursePromptPackId_returnsNullWhenOnlyHotNewsExists() {
    List<PromptPackView> promptPacks = List.of(
        PromptPackView.builder()
            .id("hot-img")
            .name(CoursePromptPackNames.IMAGE_GENERATION)
            .scene(HotNewsWorkflowKeys.SCENE)
            .scope("scene")
            .build());
    assertNull(BuiltinWorkflowSeeder.pickCoursePromptPackId(promptPacks, CoursePromptPackNames.IMAGE_GENERATION));
  }
}
