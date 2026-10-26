package com.workbench.skill.application.graphseed;

import com.workbench.skill.facade.constant.WorkflowGraphMapKeys;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作流图边（种子构建用；持久化前 {@link #toMap()}）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowGraphEdge {

  /** 边 id。 */
  private String id;
  /** 起点节点 key。 */
  private String source;
  /** 终点节点 key。 */
  private String target;

  /**
   * 转为可写入定义 JSON 的 Map。
   *
   * @return 有序 Map
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put(WorkflowGraphMapKeys.ID, id);
    map.put(WorkflowGraphMapKeys.SOURCE, source);
    map.put(WorkflowGraphMapKeys.TARGET, target);
    return map;
  }

  /**
   * 由相邻节点 key 构建线性边。
   *
   * @param sourceKey 起点 key
   * @param targetKey 终点 key
   * @return 边
   */
  public static WorkflowGraphEdge between(String sourceKey, String targetKey) {
    return WorkflowGraphEdge.builder()
        .id(WorkflowGraphMapKeys.EDGE_ID_PREFIX + sourceKey + "_" + targetKey)
        .source(sourceKey)
        .target(targetKey)
        .build();
  }
}
