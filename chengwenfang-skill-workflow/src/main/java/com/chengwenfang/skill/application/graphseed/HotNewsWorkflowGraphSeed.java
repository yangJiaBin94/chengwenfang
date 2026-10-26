package com.chengwenfang.skill.application.graphseed;

import com.chengwenfang.skill.facade.constant.HotNewsSkillNames;
import com.chengwenfang.skill.facade.enums.HotNewsWorkflowNodeType;
import com.chengwenfang.skill.facade.enums.WorkflowNodeFailurePolicy;
import com.chengwenfang.skill.facade.enums.WorkflowNodeModelType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 热点成文骨架优先主流程图种子。
 */
public final class HotNewsWorkflowGraphSeed {
  private HotNewsWorkflowGraphSeed() {}

  /** 图描述。 */
  private static final String DESCRIPTION = "热点公众号文章骨架优先生成工作流";

  /**
   * 组装热点成文规范图。
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
    nodes.add(WorkflowGraphNode.hotNewsNode(
        HotNewsWorkflowNodeType.LOAD_CONTEXT,
        "读取文章骨架与资料",
        WorkflowNodeModelType.NONE,
        60,
        1,
        WorkflowNodeFailurePolicy.STOP,
        List.of()));
    nodes.add(WorkflowGraphNode.hotNewsNode(
        HotNewsWorkflowNodeType.LOAD_SKILLS,
        "加载热点文章 Skill",
        WorkflowNodeModelType.TEXT,
        60,
        1,
        WorkflowNodeFailurePolicy.STOP,
        WorkflowSkillBinding.resolve(
            ids,
            HotNewsSkillNames.AUTHOR_STYLE,
            HotNewsSkillNames.ANTI_AI,
            HotNewsSkillNames.DRAFT_GENERATION,
            HotNewsSkillNames.DRAFT_REVIEW,
            HotNewsSkillNames.WECHAT_CONVERSION,
            HotNewsSkillNames.IMAGE_GENERATION)));
    nodes.add(WorkflowGraphNode.hotNewsNode(
        HotNewsWorkflowNodeType.PREPARE_FACTS,
        "整理资料线索为事实包",
        WorkflowNodeModelType.TEXT,
        180,
        2,
        WorkflowNodeFailurePolicy.RETRY_THEN_STOP,
        WorkflowSkillBinding.resolve(ids, HotNewsSkillNames.AUTHOR_STYLE)));
    nodes.add(WorkflowGraphNode.hotNewsNode(
        HotNewsWorkflowNodeType.GENERATE_DRAFT,
        "按骨架生成热点原稿",
        WorkflowNodeModelType.TEXT,
        300,
        3,
        WorkflowNodeFailurePolicy.RETRY_THEN_STOP,
        WorkflowSkillBinding.resolve(
            ids,
            HotNewsSkillNames.DRAFT_GENERATION,
            HotNewsSkillNames.AUTHOR_STYLE,
            HotNewsSkillNames.ANTI_AI)));
    nodes.add(WorkflowGraphNode.hotNewsNode(
        HotNewsWorkflowNodeType.REVIEW_DRAFT,
        "审校热点原稿",
        WorkflowNodeModelType.TEXT,
        300,
        3,
        WorkflowNodeFailurePolicy.RETRY_THEN_STOP,
        WorkflowSkillBinding.resolve(ids, HotNewsSkillNames.DRAFT_REVIEW, HotNewsSkillNames.ANTI_AI)));
    nodes.add(WorkflowGraphNode.hotNewsNode(
        HotNewsWorkflowNodeType.GENERATE_WECHAT,
        "生成公众号文章",
        WorkflowNodeModelType.TEXT,
        300,
        3,
        WorkflowNodeFailurePolicy.RETRY_THEN_STOP,
        WorkflowSkillBinding.resolve(
            ids,
            HotNewsSkillNames.WECHAT_CONVERSION,
            HotNewsSkillNames.AUTHOR_STYLE,
            HotNewsSkillNames.ANTI_AI)));
    nodes.add(WorkflowGraphNode.hotNewsNode(
        HotNewsWorkflowNodeType.REVIEW_WECHAT,
        "审校公众号文章",
        WorkflowNodeModelType.TEXT,
        300,
        3,
        WorkflowNodeFailurePolicy.RETRY_THEN_STOP,
        WorkflowSkillBinding.resolve(
            ids,
            HotNewsSkillNames.DRAFT_REVIEW,
            HotNewsSkillNames.WECHAT_CONVERSION,
            HotNewsSkillNames.ANTI_AI)));
    nodes.add(WorkflowGraphNode.hotNewsNode(
        HotNewsWorkflowNodeType.PLAN_IMAGES,
        "确认配套图片计划",
        WorkflowNodeModelType.NONE,
        60,
        1,
        WorkflowNodeFailurePolicy.STOP,
        List.of()));
    nodes.add(WorkflowGraphNode.hotNewsNode(
        HotNewsWorkflowNodeType.GENERATE_IMAGES,
        "生成配套图片",
        WorkflowNodeModelType.IMAGE,
        600,
        2,
        WorkflowNodeFailurePolicy.RETRY_THEN_STOP,
        WorkflowSkillBinding.resolve(ids, HotNewsSkillNames.IMAGE_GENERATION)));
    nodes.add(WorkflowGraphNode.hotNewsNode(
        HotNewsWorkflowNodeType.VALIDATE_IMAGES,
        "检查图片数量与引用",
        WorkflowNodeModelType.NONE,
        60,
        1,
        WorkflowNodeFailurePolicy.STOP,
        List.of()));
    nodes.add(WorkflowGraphNode.hotNewsNode(
        HotNewsWorkflowNodeType.FINAL_VALIDATION,
        "最终完整性检查",
        WorkflowNodeModelType.NONE,
        60,
        1,
        WorkflowNodeFailurePolicy.STOP,
        List.of()));
    nodes.add(WorkflowGraphNode.hotNewsNode(
        HotNewsWorkflowNodeType.COMMIT_VERSION,
        "保存热点文章版本",
        WorkflowNodeModelType.NONE,
        60,
        1,
        WorkflowNodeFailurePolicy.STOP,
        List.of()));
    return WorkflowGraphDefinition.linear(name, DESCRIPTION, scene, nodes);
  }

  /**
   * 组装热点成文规范图并转为持久化 Map。
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
