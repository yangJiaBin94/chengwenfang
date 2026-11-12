package com.chengwenfang.prompt.application.graphseed;

import com.chengwenfang.prompt.facade.constant.HotNewsPromptPackNames;
import com.chengwenfang.prompt.facade.enums.HotNewsWorkflowNodeType;
import com.chengwenfang.prompt.facade.enums.WorkflowNodeFailurePolicy;
import com.chengwenfang.prompt.facade.enums.WorkflowNodeModelType;
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
   * @param promptPackIdsByName 提示词包 名 → id
   * @return 类型化定义
   */
  public static WorkflowGraphDefinition build(
      String name, String scene, Map<String, String> promptPackIdsByName) {
    Map<String, String> ids = promptPackIdsByName == null ? Map.of() : promptPackIdsByName;
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
        HotNewsWorkflowNodeType.LOAD_PROMPT_PACKS,
        "加载热点文章 提示词包",
        WorkflowNodeModelType.TEXT,
        60,
        1,
        WorkflowNodeFailurePolicy.STOP,
        WorkflowPromptPackBinding.resolve(
            ids,
            HotNewsPromptPackNames.AUTHOR_STYLE,
            HotNewsPromptPackNames.ANTI_AI,
            HotNewsPromptPackNames.DRAFT_GENERATION,
            HotNewsPromptPackNames.DRAFT_REVIEW,
            HotNewsPromptPackNames.WECHAT_CONVERSION,
            HotNewsPromptPackNames.IMAGE_GENERATION)));
    nodes.add(WorkflowGraphNode.hotNewsNode(
        HotNewsWorkflowNodeType.PREPARE_FACTS,
        "整理资料线索为事实包",
        WorkflowNodeModelType.TEXT,
        180,
        2,
        WorkflowNodeFailurePolicy.RETRY_THEN_STOP,
        WorkflowPromptPackBinding.resolve(ids, HotNewsPromptPackNames.AUTHOR_STYLE)));
    nodes.add(WorkflowGraphNode.hotNewsNode(
        HotNewsWorkflowNodeType.GENERATE_DRAFT,
        "按骨架生成热点原稿",
        WorkflowNodeModelType.TEXT,
        300,
        3,
        WorkflowNodeFailurePolicy.RETRY_THEN_STOP,
        WorkflowPromptPackBinding.resolve(
            ids,
            HotNewsPromptPackNames.DRAFT_GENERATION,
            HotNewsPromptPackNames.AUTHOR_STYLE,
            HotNewsPromptPackNames.ANTI_AI)));
    nodes.add(WorkflowGraphNode.hotNewsNode(
        HotNewsWorkflowNodeType.REVIEW_DRAFT,
        "审校热点原稿",
        WorkflowNodeModelType.TEXT,
        300,
        3,
        WorkflowNodeFailurePolicy.RETRY_THEN_STOP,
        WorkflowPromptPackBinding.resolve(ids, HotNewsPromptPackNames.DRAFT_REVIEW, HotNewsPromptPackNames.ANTI_AI)));
    nodes.add(WorkflowGraphNode.hotNewsNode(
        HotNewsWorkflowNodeType.ATTACH_EVIDENCE,
        "附加来源佐证截屏",
        WorkflowNodeModelType.TEXT,
        420,
        2,
        WorkflowNodeFailurePolicy.CONTINUE_WITH_WARNING,
        WorkflowPromptPackBinding.resolve(ids, HotNewsPromptPackNames.AUTHOR_STYLE)));
    nodes.add(WorkflowGraphNode.hotNewsNode(
        HotNewsWorkflowNodeType.GENERATE_WECHAT,
        "生成公众号文章",
        WorkflowNodeModelType.TEXT,
        300,
        3,
        WorkflowNodeFailurePolicy.RETRY_THEN_STOP,
        WorkflowPromptPackBinding.resolve(
            ids,
            HotNewsPromptPackNames.WECHAT_CONVERSION,
            HotNewsPromptPackNames.AUTHOR_STYLE,
            HotNewsPromptPackNames.ANTI_AI)));
    nodes.add(WorkflowGraphNode.hotNewsNode(
        HotNewsWorkflowNodeType.REVIEW_WECHAT,
        "审校公众号文章",
        WorkflowNodeModelType.TEXT,
        300,
        3,
        WorkflowNodeFailurePolicy.RETRY_THEN_STOP,
        WorkflowPromptPackBinding.resolve(
            ids,
            HotNewsPromptPackNames.DRAFT_REVIEW,
            HotNewsPromptPackNames.WECHAT_CONVERSION,
            HotNewsPromptPackNames.ANTI_AI)));
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
        WorkflowPromptPackBinding.resolve(ids, HotNewsPromptPackNames.IMAGE_GENERATION)));
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
   * @param promptPackIdsByName 提示词包 名 → id
   * @return 定义 Map
   */
  public static Map<String, Object> toDefinitionMap(
      String name, String scene, Map<String, String> promptPackIdsByName) {
    return build(name, scene, promptPackIdsByName).toMap();
  }
}
