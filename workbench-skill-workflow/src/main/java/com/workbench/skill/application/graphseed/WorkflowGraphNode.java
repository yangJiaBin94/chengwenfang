package com.workbench.skill.application.graphseed;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.workbench.skill.facade.constant.WorkflowGraphMapKeys;
import com.workbench.skill.facade.enums.CourseWorkflowNodeType;
import com.workbench.skill.facade.enums.HotNewsRadarCollectNodeType;
import com.workbench.skill.facade.enums.HotNewsWorkflowNodeType;
import com.workbench.skill.facade.enums.WorkflowNodeFailurePolicy;
import com.workbench.skill.facade.enums.WorkflowNodeModelType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作流图节点（种子构建用；持久化前 {@link #toMap()}）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowGraphNode {

  /** 节点 id（通常与 typeKey 相同）。 */
  private String id;
  /** 节点 key（边引用）。 */
  private String key;
  /** 规范类型键。 */
  private String typeKey;
  /** 兼容旧字段的类型键。 */
  private String type;
  /** 展示名。 */
  private String name;
  /** 设计器标签。 */
  private String label;
  /** 是否启用。 */
  private Boolean enabled;
  /** 运行配置。 */
  private WorkflowNodeConfig config;
  /** Skill 绑定。 */
  private List<WorkflowSkillBinding> skillBindings;

  /**
   * 转为可写入定义 JSON 的 Map。
   *
   * @return 有序 Map
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put(WorkflowGraphMapKeys.ID, id);
    map.put(WorkflowGraphMapKeys.KEY, key);
    map.put(WorkflowGraphMapKeys.TYPE_KEY, typeKey);
    map.put(WorkflowGraphMapKeys.TYPE, type);
    map.put(WorkflowGraphMapKeys.NAME, name);
    map.put(WorkflowGraphMapKeys.LABEL, label);
    map.put(WorkflowGraphMapKeys.ENABLED, enabled == null || enabled);
    map.put(WorkflowGraphMapKeys.CONFIG, config == null ? Map.of() : config.toMap());
    List<Map<String, Object>> bindings = new ArrayList<>();
    if (CollUtil.isNotEmpty(skillBindings)) {
      for (WorkflowSkillBinding binding : skillBindings) {
        bindings.add(binding.toMap());
      }
    }
    map.put(WorkflowGraphMapKeys.SKILL_BINDINGS, bindings);
    return map;
  }

  /**
   * 构建课程工作流节点。
   *
   * @param nodeType       节点枚举
   * @param timeoutSeconds 超时秒数
   * @param maxAttempts    最大尝试次数
   * @param failurePolicy  失败策略
   * @param skillBindings  Skill 绑定
   * @return 类型化节点
   */
  public static WorkflowGraphNode courseNode(
      CourseWorkflowNodeType nodeType,
      int timeoutSeconds,
      int maxAttempts,
      WorkflowNodeFailurePolicy failurePolicy,
      List<WorkflowSkillBinding> skillBindings) {
    WorkflowNodeModelType modelType = WorkflowNodeModelType.from(nodeType.getModelType());
    // 进度步与 typeKey 不同时才写入 progressStepKey，避免冗余
    String progressStepKey = null;
    if (!nodeType.getProgressStepKey().equals(nodeType.getValue())) {
      progressStepKey = nodeType.getProgressStepKey();
    }
    return WorkflowGraphNode.builder()
        .id(nodeType.getValue())
        .key(nodeType.getValue())
        .typeKey(nodeType.getValue())
        .type(nodeType.getValue())
        .name(nodeType.getDisplayName())
        .label(nodeType.getDisplayName())
        .enabled(true)
        .config(WorkflowNodeConfig.of(
            timeoutSeconds, maxAttempts, failurePolicy, modelType, progressStepKey))
        .skillBindings(skillBindings == null ? List.of() : skillBindings)
        .build();
  }

  /**
   * 构建热点成文工作流节点。
   *
   * @param nodeType       节点枚举
   * @param displayName    展示名
   * @param modelType      模型类型
   * @param timeoutSeconds 超时秒数
   * @param maxAttempts    最大尝试次数
   * @param failurePolicy  失败策略
   * @param skillBindings  Skill 绑定
   * @return 类型化节点
   */
  public static WorkflowGraphNode hotNewsNode(
      HotNewsWorkflowNodeType nodeType,
      String displayName,
      WorkflowNodeModelType modelType,
      int timeoutSeconds,
      int maxAttempts,
      WorkflowNodeFailurePolicy failurePolicy,
      List<WorkflowSkillBinding> skillBindings) {
    String typeKey = nodeType.getValue();
    return WorkflowGraphNode.builder()
        .id(typeKey)
        .key(typeKey)
        .typeKey(typeKey)
        .type(typeKey)
        .name(displayName)
        .label(displayName)
        .enabled(true)
        .config(WorkflowNodeConfig.of(timeoutSeconds, maxAttempts, failurePolicy, modelType))
        .skillBindings(skillBindings == null ? List.of() : skillBindings)
        .build();
  }

  /**
   * 构建雷达采集工作流节点。
   *
   * @param nodeType       节点枚举
   * @param timeoutSeconds 超时秒数
   * @param maxAttempts    最大尝试次数
   * @param failurePolicy  失败策略
   * @param skillBindings  Skill 绑定
   * @return 类型化节点
   */
  public static WorkflowGraphNode radarNode(
      HotNewsRadarCollectNodeType nodeType,
      int timeoutSeconds,
      int maxAttempts,
      WorkflowNodeFailurePolicy failurePolicy,
      List<WorkflowSkillBinding> skillBindings) {
    WorkflowNodeModelType modelType = WorkflowNodeModelType.from(nodeType.getModelType());
    return WorkflowGraphNode.builder()
        .id(nodeType.getValue())
        .key(nodeType.getValue())
        .typeKey(nodeType.getValue())
        .type(nodeType.getValue())
        .name(nodeType.getDisplayName())
        .label(nodeType.getDisplayName())
        .enabled(true)
        .config(WorkflowNodeConfig.of(timeoutSeconds, maxAttempts, failurePolicy, modelType))
        .skillBindings(skillBindings == null ? List.of() : skillBindings)
        .build();
  }

  /**
   * 读取节点 key；空白时回落 typeKey。
   *
   * @return 边引用用的 key
   */
  public String resolveKey() {
    if (StrUtil.isNotBlank(key)) {
      return key;
    }
    return typeKey;
  }
}
