package com.chengwenfang.skill.application.graphseed;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.skill.facade.constant.WorkflowGraphMapKeys;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 内置工作流图定义（种子构建用；持久化前 {@link #toMap()}）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowGraphDefinition {

  /** schema 版本字面量。 */
  private String schemaVersion;
  /** 展示名。 */
  private String name;
  /** 描述。 */
  private String description;
  /** 场景码。 */
  private String scene;
  /** 模式 id（可空字符串）。 */
  private String modeId;
  /** 节点列表。 */
  private List<WorkflowGraphNode> nodes;
  /** 边列表。 */
  private List<WorkflowGraphEdge> edges;

  /**
   * 转为可写入草稿 / 版本 JSON 的 Map。
   *
   * @return 有序 Map
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put(
        WorkflowGraphMapKeys.SCHEMA_VERSION,
        StrUtil.blankToDefault(schemaVersion, WorkflowGraphMapKeys.SCHEMA_VERSION_V1));
    map.put(WorkflowGraphMapKeys.NAME, name);
    map.put(WorkflowGraphMapKeys.DESCRIPTION, description);
    map.put(WorkflowGraphMapKeys.SCENE, scene);
    map.put(WorkflowGraphMapKeys.MODE_ID, modeId == null ? "" : modeId);
    List<Map<String, Object>> nodeMaps = new ArrayList<>();
    if (CollUtil.isNotEmpty(nodes)) {
      for (WorkflowGraphNode node : nodes) {
        nodeMaps.add(node.toMap());
      }
    }
    map.put(WorkflowGraphMapKeys.NODES, nodeMaps);
    List<Map<String, Object>> edgeMaps = new ArrayList<>();
    if (CollUtil.isNotEmpty(edges)) {
      for (WorkflowGraphEdge edge : edges) {
        edgeMaps.add(edge.toMap());
      }
    }
    map.put(WorkflowGraphMapKeys.EDGES, edgeMaps);
    return map;
  }

  /**
   * 组装规范图：schema v1、空 modeId，并按节点顺序生成线性边。
   *
   * @param name        展示名
   * @param description 描述
   * @param scene       场景码
   * @param nodes       有序节点
   * @return 类型化定义
   */
  public static WorkflowGraphDefinition linear(
      String name, String description, String scene, List<WorkflowGraphNode> nodes) {
    List<WorkflowGraphNode> safeNodes = nodes == null ? List.of() : nodes;
    return WorkflowGraphDefinition.builder()
        .schemaVersion(WorkflowGraphMapKeys.SCHEMA_VERSION_V1)
        .name(name)
        .description(description)
        .scene(scene)
        .modeId("")
        .nodes(safeNodes)
        .edges(linearEdges(safeNodes))
        .build();
  }

  /**
   * 按节点顺序生成相邻线性边。
   *
   * @param nodes 有序节点
   * @return 边列表
   */
  public static List<WorkflowGraphEdge> linearEdges(List<WorkflowGraphNode> nodes) {
    List<WorkflowGraphEdge> edges = new ArrayList<>();
    if (CollUtil.isEmpty(nodes) || nodes.size() < 2) {
      return edges;
    }
    for (int index = 0; index < nodes.size() - 1; index++) {
      String source = nodes.get(index).resolveKey();
      String target = nodes.get(index + 1).resolveKey();
      edges.add(WorkflowGraphEdge.between(source, target));
    }
    return edges;
  }
}
