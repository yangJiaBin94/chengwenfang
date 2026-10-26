package com.chengwenfang.skill.application.graphseed;

import com.chengwenfang.skill.facade.constant.CourseSkillNames;
import com.chengwenfang.skill.facade.enums.CourseWorkflowNodeType;
import com.chengwenfang.skill.facade.enums.WorkflowNodeFailurePolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 课程成文规范主流程图种子（14 节点线性）。
 */
public final class CourseWorkflowGraphSeed {
  private CourseWorkflowGraphSeed() {}

  /** 图描述。 */
  private static final String DESCRIPTION = "课程内容生成规范主流程";

  /**
   * 组装课程规范主流程图。
   *
   * @param name           展示名
   * @param scene          场景码
   * @param skillIdsByName Skill 名 → id
   * @return 类型化定义
   */
  public static WorkflowGraphDefinition build(
      String name, String scene, Map<String, String> skillIdsByName) {
    Map<String, String> ids = skillIdsByName == null ? Map.of() : skillIdsByName;
    List<WorkflowGraphNode> nodes = new ArrayList<>();
    nodes.add(WorkflowGraphNode.courseNode(
        CourseWorkflowNodeType.SNAPSHOT_CONTEXT,
        60,
        1,
        WorkflowNodeFailurePolicy.STOP,
        WorkflowSkillBinding.resolve(ids, CourseSkillNames.CHENGWENFANG_RULES)));
    nodes.add(WorkflowGraphNode.courseNode(
        CourseWorkflowNodeType.DRAFT_OPENING,
        300,
        3,
        WorkflowNodeFailurePolicy.RETRY_THEN_STOP,
        WorkflowSkillBinding.resolve(
            ids,
            CourseSkillNames.COURSE_DRAFT,
            CourseSkillNames.AUTHOR_STYLE,
            CourseSkillNames.CHENGWENFANG_RULES,
            CourseSkillNames.COURSE_REVIEW)));
    nodes.add(WorkflowGraphNode.courseNode(
        CourseWorkflowNodeType.DRAFT_CORE,
        300,
        3,
        WorkflowNodeFailurePolicy.RETRY_THEN_STOP,
        WorkflowSkillBinding.resolve(
            ids,
            CourseSkillNames.COURSE_DRAFT,
            CourseSkillNames.AUTHOR_STYLE,
            CourseSkillNames.CHENGWENFANG_RULES,
            CourseSkillNames.COURSE_REVIEW)));
    nodes.add(WorkflowGraphNode.courseNode(
        CourseWorkflowNodeType.DRAFT_EXAMPLE,
        300,
        3,
        WorkflowNodeFailurePolicy.RETRY_THEN_STOP,
        WorkflowSkillBinding.resolve(
            ids,
            CourseSkillNames.COURSE_DRAFT,
            CourseSkillNames.AUTHOR_STYLE,
            CourseSkillNames.CHENGWENFANG_RULES,
            CourseSkillNames.COURSE_REVIEW,
            CourseSkillNames.IMAGE_PLANNING)));
    nodes.add(WorkflowGraphNode.courseNode(
        CourseWorkflowNodeType.DRAFT_SUMMARY,
        180,
        3,
        WorkflowNodeFailurePolicy.RETRY_THEN_STOP,
        WorkflowSkillBinding.resolve(
            ids,
            CourseSkillNames.COURSE_DRAFT,
            CourseSkillNames.AUTHOR_STYLE,
            CourseSkillNames.CHENGWENFANG_RULES,
            CourseSkillNames.COURSE_REVIEW,
            CourseSkillNames.FINALIZATION)));
    nodes.add(WorkflowGraphNode.courseNode(
        CourseWorkflowNodeType.DRAFT_PREVIEW,
        180,
        3,
        WorkflowNodeFailurePolicy.RETRY_THEN_STOP,
        WorkflowSkillBinding.resolve(
            ids,
            CourseSkillNames.COURSE_DRAFT,
            CourseSkillNames.AUTHOR_STYLE,
            CourseSkillNames.CHENGWENFANG_RULES,
            CourseSkillNames.COURSE_REVIEW,
            CourseSkillNames.FINALIZATION)));
    // 业务 repair 在节点内最多 3 次；图级 maxAttempts 保持 1，避免双重重试
    nodes.add(WorkflowGraphNode.courseNode(
        CourseWorkflowNodeType.REVIEW_DRAFT,
        180,
        1,
        WorkflowNodeFailurePolicy.STOP,
        WorkflowSkillBinding.resolve(ids, CourseSkillNames.COURSE_REVIEW)));
    nodes.add(WorkflowGraphNode.courseNode(
        CourseWorkflowNodeType.WECHAT_CONVERSION,
        300,
        1,
        WorkflowNodeFailurePolicy.STOP,
        WorkflowSkillBinding.resolve(
            ids,
            CourseSkillNames.WECHAT_CONVERSION,
            CourseSkillNames.AUTHOR_STYLE,
            CourseSkillNames.CHENGWENFANG_RULES,
            CourseSkillNames.COURSE_REVIEW,
            CourseSkillNames.FINALIZATION)));
    nodes.add(WorkflowGraphNode.courseNode(
        CourseWorkflowNodeType.IMAGE_COVER,
        600,
        2,
        WorkflowNodeFailurePolicy.RETRY_THEN_STOP,
        WorkflowSkillBinding.resolve(
            ids, CourseSkillNames.IMAGE_GENERATION, CourseSkillNames.IMAGE_PLANNING)));
    nodes.add(WorkflowGraphNode.courseNode(
        CourseWorkflowNodeType.IMAGE_INLINE,
        600,
        2,
        WorkflowNodeFailurePolicy.RETRY_THEN_STOP,
        WorkflowSkillBinding.resolve(
            ids, CourseSkillNames.IMAGE_GENERATION, CourseSkillNames.IMAGE_PLANNING)));
    nodes.add(WorkflowGraphNode.courseNode(
        CourseWorkflowNodeType.FORMULA_STYLE_VALIDATION,
        60,
        1,
        WorkflowNodeFailurePolicy.STOP,
        List.of()));
    nodes.add(WorkflowGraphNode.courseNode(
        CourseWorkflowNodeType.FORMULA_EXPRESSION_VALIDATION,
        60,
        1,
        WorkflowNodeFailurePolicy.STOP,
        List.of()));
    nodes.add(WorkflowGraphNode.courseNode(
        CourseWorkflowNodeType.FINAL_VALIDATION,
        60,
        1,
        WorkflowNodeFailurePolicy.STOP,
        List.of()));
    nodes.add(WorkflowGraphNode.courseNode(
        CourseWorkflowNodeType.COMMIT_VERSION,
        60,
        1,
        WorkflowNodeFailurePolicy.STOP,
        List.of()));
    return WorkflowGraphDefinition.linear(name, DESCRIPTION, scene, nodes);
  }

  /**
   * 组装课程规范图并转为持久化 Map。
   *
   * @param name           展示名
   * @param scene          场景码
   * @param skillIdsByName Skill 名 → id
   * @return 定义 Map
   */
  public static Map<String, Object> toDefinitionMap(
      String name, String scene, Map<String, String> skillIdsByName) {
    return build(name, scene, skillIdsByName).toMap();
  }
}
