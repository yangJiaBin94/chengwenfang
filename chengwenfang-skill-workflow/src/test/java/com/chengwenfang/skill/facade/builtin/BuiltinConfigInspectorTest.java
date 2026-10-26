package com.chengwenfang.skill.facade.builtin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.chengwenfang.skill.application.WorkflowFacadeImpl;
import com.chengwenfang.skill.facade.builtin.BuiltinConfigInspector.ExpectedSkill;
import com.chengwenfang.skill.facade.builtin.BuiltinConfigInspector.SkillRow;
import com.chengwenfang.skill.facade.builtin.BuiltinConfigInspector.WorkflowRow;
import com.chengwenfang.skill.facade.constant.CourseWorkflowKeys;
import com.chengwenfang.skill.facade.constant.HotNewsRadarCollectWorkflowKeys;
import com.chengwenfang.skill.facade.constant.HotNewsWorkflowKeys;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@link BuiltinConfigInspector} 缺项与齐全两种结果。 */
class BuiltinConfigInspectorTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void inspect_reportsMissingSkillAndWorkflow() {
    List<BuiltinIssue> issues = BuiltinConfigInspector.inspect(List.of(), List.of());
    assertTrue(issues.stream().anyMatch(item -> item.message().contains("缺少 Skill")));
    assertTrue(issues.stream().anyMatch(item ->
        item.isWorkflow()
            && item.message().contains("缺少已发布工作流 key=" + CourseWorkflowKeys.DEFINITION_KEY)));
  }

  @Test
  void inspect_passesWhenExpectedRowsPresent() throws Exception {
    List<SkillRow> skills = new ArrayList<>();
    Map<String, String> identity = new LinkedHashMap<>();
    for (ExpectedSkill expected : BuiltinConfigInspector.expectedSkills()) {
      String id = expected.scene() + ":" + expected.name();
      skills.add(new SkillRow(id, expected.name(), expected.scene(), true, "prompt"));
      identity.put(expected.name(), id);
    }
    List<WorkflowRow> workflows = List.of(
        published(CourseWorkflowKeys.DEFINITION_KEY, WorkflowFacadeImpl.exportCourseContentDefinition(identity)),
        published(HotNewsWorkflowKeys.DEFINITION_KEY, WorkflowFacadeImpl.exportHotNewsArticleDefinition(identity)),
        published(
            HotNewsRadarCollectWorkflowKeys.DEFINITION_KEY,
            WorkflowFacadeImpl.exportRadarCollectDefinition(identity)));
    List<BuiltinIssue> issues = BuiltinConfigInspector.inspect(skills, workflows);
    assertTrue(issues.isEmpty(), () -> BuiltinConfigInspector.messageList(issues).toString());
  }

  @Test
  void inspect_flagsBlankInstructions() {
    ExpectedSkill first = BuiltinConfigInspector.expectedSkills().get(0);
    List<BuiltinIssue> issues = BuiltinConfigInspector.inspect(
        List.of(new SkillRow("1", first.name(), first.scene(), true, "  ")),
        List.of());
    assertFalse(issues.stream().noneMatch(item -> item.message().contains("系统指令为空")));
  }

  private WorkflowRow published(String key, Map<String, Object> definition) throws Exception {
    return new WorkflowRow(key, "ver-" + key, mapper.writeValueAsString(definition));
  }
}
