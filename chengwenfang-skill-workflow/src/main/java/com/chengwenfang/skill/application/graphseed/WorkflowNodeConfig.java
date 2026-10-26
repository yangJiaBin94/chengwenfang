package com.chengwenfang.skill.application.graphseed;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.skill.facade.constant.WorkflowGraphMapKeys;
import com.chengwenfang.skill.facade.enums.WorkflowNodeFailurePolicy;
import com.chengwenfang.skill.facade.enums.WorkflowNodeModelType;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作流节点运行配置（种子构建用；持久化前 {@link #toMap()}）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowNodeConfig {

  /** 节点执行超时（秒）。 */
  private Integer timeoutSeconds;
  /** 图级最大尝试次数。 */
  private Integer maxAttempts;
  /** 失败策略。 */
  private WorkflowNodeFailurePolicy failurePolicy;
  /** 模型类型。 */
  private WorkflowNodeModelType modelType;
  /** 前端进度步名；与 typeKey 相同时可省略写入。 */
  private String progressStepKey;

  /**
   * 转为可写入定义 JSON 的 Map。
   *
   * @return 有序 Map
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put(WorkflowGraphMapKeys.TIMEOUT_SECONDS, timeoutSeconds);
    map.put(WorkflowGraphMapKeys.MAX_ATTEMPTS, maxAttempts);
    map.put(
        WorkflowGraphMapKeys.FAILURE_POLICY,
        failurePolicy == null ? WorkflowNodeFailurePolicy.STOP.getValue() : failurePolicy.getValue());
    map.put(
        WorkflowGraphMapKeys.MODEL_TYPE,
        modelType == null ? WorkflowNodeModelType.NONE.getValue() : modelType.getValue());
    if (StrUtil.isNotBlank(progressStepKey)) {
      map.put(WorkflowGraphMapKeys.PROGRESS_STEP_KEY, progressStepKey);
    }
    return map;
  }

  /**
   * 构建基础配置（无进度步别名）。
   *
   * @param timeoutSeconds 超时秒数
   * @param maxAttempts    最大尝试次数
   * @param failurePolicy  失败策略
   * @param modelType      模型类型
   * @return 配置
   */
  public static WorkflowNodeConfig of(
      int timeoutSeconds,
      int maxAttempts,
      WorkflowNodeFailurePolicy failurePolicy,
      WorkflowNodeModelType modelType) {
    return WorkflowNodeConfig.builder()
        .timeoutSeconds(timeoutSeconds)
        .maxAttempts(maxAttempts)
        .failurePolicy(failurePolicy)
        .modelType(modelType)
        .build();
  }

  /**
   * 构建带进度步别名的配置。
   *
   * @param timeoutSeconds  超时秒数
   * @param maxAttempts     最大尝试次数
   * @param failurePolicy   失败策略
   * @param modelType       模型类型
   * @param progressStepKey 进度步名
   * @return 配置
   */
  public static WorkflowNodeConfig of(
      int timeoutSeconds,
      int maxAttempts,
      WorkflowNodeFailurePolicy failurePolicy,
      WorkflowNodeModelType modelType,
      String progressStepKey) {
    return WorkflowNodeConfig.builder()
        .timeoutSeconds(timeoutSeconds)
        .maxAttempts(maxAttempts)
        .failurePolicy(failurePolicy)
        .modelType(modelType)
        .progressStepKey(progressStepKey)
        .build();
  }
}
