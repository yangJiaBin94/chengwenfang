package com.chengwenfang.skill.application.graphseed;

import com.chengwenfang.skill.facade.constant.HotNewsRadarCollectSkillNames;
import com.chengwenfang.skill.facade.enums.HotNewsRadarCollectNodeType;
import com.chengwenfang.skill.facade.enums.WorkflowNodeFailurePolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 热点雷达采集主流程图种子。
 */
public final class RadarCollectWorkflowGraphSeed {
  private RadarCollectWorkflowGraphSeed() {}

  /** 图描述。 */
  private static final String DESCRIPTION =
      "热点雷达同步采集：获取→解析速览→素材筛选→成簇→AI主题素材→Top10";

  /**
   * 组装雷达采集规范图。
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
    nodes.add(WorkflowGraphNode.radarNode(
        HotNewsRadarCollectNodeType.LOAD_CONTEXT,
        60,
        1,
        WorkflowNodeFailurePolicy.STOP,
        List.of()));
    nodes.add(WorkflowGraphNode.radarNode(
        HotNewsRadarCollectNodeType.LOAD_SKILLS,
        60,
        1,
        WorkflowNodeFailurePolicy.STOP,
        WorkflowSkillBinding.resolve(
            ids,
            HotNewsRadarCollectSkillNames.ARTICLE_FACT_BRIEF,
            HotNewsRadarCollectSkillNames.ARTICLE_PUBLISH_DATE,
            HotNewsRadarCollectSkillNames.VIDEO_FACT_BRIEF,
            HotNewsRadarCollectSkillNames.MARKETING_FILTER,
            HotNewsRadarCollectSkillNames.CLUSTER_SUMMARY_MERGE)));
    nodes.add(WorkflowGraphNode.radarNode(
        HotNewsRadarCollectNodeType.FETCH_CHANNELS,
        600,
        1,
        WorkflowNodeFailurePolicy.STOP,
        List.of()));
    nodes.add(WorkflowGraphNode.radarNode(
        HotNewsRadarCollectNodeType.ENRICH_SUMMARIZE,
        900,
        1,
        WorkflowNodeFailurePolicy.STOP,
        WorkflowSkillBinding.resolve(
            ids,
            HotNewsRadarCollectSkillNames.ARTICLE_FACT_BRIEF,
            HotNewsRadarCollectSkillNames.ARTICLE_PUBLISH_DATE,
            HotNewsRadarCollectSkillNames.VIDEO_FACT_BRIEF)));
    nodes.add(WorkflowGraphNode.radarNode(
        HotNewsRadarCollectNodeType.CANDIDATE_FILTER,
        600,
        1,
        WorkflowNodeFailurePolicy.STOP,
        WorkflowSkillBinding.resolve(ids, HotNewsRadarCollectSkillNames.MARKETING_FILTER)));
    nodes.add(WorkflowGraphNode.radarNode(
        HotNewsRadarCollectNodeType.PERSIST_CLUSTER,
        600,
        1,
        WorkflowNodeFailurePolicy.STOP,
        WorkflowSkillBinding.resolve(ids, HotNewsRadarCollectSkillNames.CLUSTER_SUMMARY_MERGE)));
    nodes.add(WorkflowGraphNode.radarNode(
        HotNewsRadarCollectNodeType.AI_THEME_SEED,
        180,
        1,
        WorkflowNodeFailurePolicy.STOP,
        WorkflowSkillBinding.resolve(ids, HotNewsRadarCollectSkillNames.AI_THEME_SEED)));
    nodes.add(WorkflowGraphNode.radarNode(
        HotNewsRadarCollectNodeType.SCORE_TOP10,
        120,
        1,
        WorkflowNodeFailurePolicy.STOP,
        List.of()));
    nodes.add(WorkflowGraphNode.radarNode(
        HotNewsRadarCollectNodeType.FINALIZE,
        60,
        1,
        WorkflowNodeFailurePolicy.STOP,
        List.of()));
    return WorkflowGraphDefinition.linear(name, DESCRIPTION, scene, nodes);
  }

  /**
   * 组装雷达采集规范图并转为持久化 Map。
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
