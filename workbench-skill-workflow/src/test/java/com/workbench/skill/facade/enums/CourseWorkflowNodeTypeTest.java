package com.workbench.skill.facade.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.workbench.skill.facade.constant.WorkflowStructuralNodeKeys;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** {@link CourseWorkflowNodeType} 单元测试。 */
class CourseWorkflowNodeTypeTest {

  @Test
  void builtinTypeKeys_hasFourteenNodes() {
    assertEquals(14, CourseWorkflowNodeType.builtinTypeKeys().size());
    assertEquals(14, CourseWorkflowNodeType.builtinSet().size());
    assertEquals(14, CourseWorkflowNodeType.values().length);
  }

  @Test
  void isFullCourseGraph_requiresAllBuiltinKeys() {
    Set<String> full = new HashSet<>(CourseWorkflowNodeType.builtinTypeKeys());
    assertTrue(CourseWorkflowNodeType.isFullCourseGraph(full));

    full.add(WorkflowStructuralNodeKeys.CUSTOM_AI);
    assertTrue(CourseWorkflowNodeType.isFullCourseGraph(full));

    Set<String> missing = new HashSet<>(CourseWorkflowNodeType.builtinTypeKeys());
    missing.remove(CourseWorkflowNodeType.COMMIT_VERSION.getValue());
    assertFalse(CourseWorkflowNodeType.isFullCourseGraph(missing));
    assertFalse(CourseWorkflowNodeType.isFullCourseGraph(null));
    assertFalse(CourseWorkflowNodeType.isFullCourseGraph(Set.of()));
  }

  @Test
  void progressStepKey_aliases() {
    assertEquals("generate_wechat", CourseWorkflowNodeType.WECHAT_CONVERSION.getProgressStepKey());
    assertEquals("finalize", CourseWorkflowNodeType.COMMIT_VERSION.getProgressStepKey());
  }
}
