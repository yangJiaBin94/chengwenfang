package com.workbench.mode.course.application.generation.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link CourseGraphState} 与 Map 互转冒烟测试。
 */
class CourseGraphStateTest {

  @Test
  void fromMap_toMap_roundTripPreservesCoreFields() {
    CourseGraphState state = CourseGraphState.builder()
        .courseId("c1")
        .jobId("j1")
        .workflowId("w1")
        .draft("draft-body")
        .wechat("wechat-body")
        .draftParts(List.of("a", "b"))
        .coverDone(true)
        .committed(false)
        .build();
    Map<String, Object> map = state.toMap();
    CourseGraphState restored = CourseGraphState.fromMap(map);
    assertEquals("c1", restored.getCourseId());
    assertEquals("j1", restored.getJobId());
    assertEquals("draft-body", restored.getDraft());
    assertEquals("wechat-body", restored.getWechat());
    assertEquals(2, restored.getDraftParts().size());
    assertTrue(Boolean.TRUE.equals(restored.getCoverDone()));
    assertFalse(Boolean.TRUE.equals(restored.getCommitted()));
  }

  @Test
  void toJobOutputShell_containsDraftWechatAndShell() {
    CourseGraphState state = CourseGraphState.builder()
        .courseId("c1")
        .workflowId("w1")
        .title("T")
        .draft("D")
        .wechat("W")
        .draftArticleId("da")
        .wechatArticleId("wa")
        .images(List.of(Map.of("id", "img1")))
        .build();
    Map<String, Object> shell = state.toJobOutputShell();
    assertEquals("D", shell.get(CourseGraphStateKeys.DRAFT));
    assertEquals("W", shell.get(CourseGraphStateKeys.WECHAT));
    assertTrue(shell.containsKey("shell"));
    assertTrue(shell.containsKey(CourseGraphStateKeys.IMAGES));
  }
}
