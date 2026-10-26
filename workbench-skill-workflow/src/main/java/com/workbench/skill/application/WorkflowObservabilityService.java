package com.workbench.skill.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.common.BusinessException;
import com.workbench.common.ErrorCodes;
import com.workbench.skill.domain.entity.WorkflowAlertEntity;
import com.workbench.skill.domain.entity.WorkflowNodeRunEntity;
import com.workbench.skill.domain.entity.WorkflowRunEntity;
import com.workbench.skill.domain.entity.WorkflowVersionEntity;
import com.workbench.skill.facade.enums.WorkflowAlertStatus;
import com.workbench.skill.facade.enums.WorkflowRunStatus;
import com.workbench.skill.facade.dto.view.WorkflowAlertView;
import com.workbench.skill.facade.dto.view.WorkflowFailedNodeMetricView;
import com.workbench.skill.facade.dto.view.WorkflowMetricsView;
import com.workbench.skill.facade.dto.view.WorkflowNodeMetricView;
import com.workbench.skill.facade.dto.view.WorkflowVersionMetricView;
import com.workbench.skill.infrastructure.mapper.WorkflowAlertMapper;
import com.workbench.skill.infrastructure.mapper.WorkflowNodeRunMapper;
import com.workbench.skill.infrastructure.mapper.WorkflowRunMapper;
import com.workbench.skill.infrastructure.mapper.WorkflowVersionMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 工作流可观测性：运行指标聚合与告警读写。
 * <p>由 WorkflowFacadeImpl 薄委托，不对外暴露。
 */
@Service
@RequiredArgsConstructor
public class WorkflowObservabilityService {

  /** 工作流运行表。 */
  private final WorkflowRunMapper runMapper;
  /** 节点运行表。 */
  private final WorkflowNodeRunMapper nodeRunMapper;
  /** 告警表。 */
  private final WorkflowAlertMapper alertMapper;
  /** 版本表（版本指标标签）。 */
  private final WorkflowVersionMapper versionMapper;
  /** 共享工具。 */
  private final WorkflowSupport support;

  /**
   * 汇总某工作流运行指标：次数、成功率、平均/P95 耗时、失败节点排行。
   *
   * @param id 工作流定义主键
   * @return 指标汇总视图
   * @throws BusinessException 定义不存在
   */
  public WorkflowMetricsView metricsSummary(String id) {
    support.require(id);
    List<WorkflowRunEntity> runs = runMapper.selectList(new LambdaQueryWrapper<WorkflowRunEntity>()
        .eq(WorkflowRunEntity::getWorkflowDefinitionId, id)
        .orderByDesc(WorkflowRunEntity::getCreatedAt));
    int total = runs.size();
    // 成功态按 WorkflowRunStatus.SUCCEEDED 计数
    long succeeded = runs.stream()
        .filter(run -> WorkflowRunStatus.SUCCEEDED.getValue().equals(run.getStatus()))
        .count();
    // 仅统计有起止时间的运行，得到秒级耗时序列
    List<Long> durations = runs.stream()
        .filter(run -> run.getCreatedAt() != null && run.getFinishedAt() != null)
        .map(run -> Duration.between(run.getCreatedAt(), run.getFinishedAt()).getSeconds())
        .filter(seconds -> seconds >= 0)
        .sorted()
        .toList();
    double avg = durations.isEmpty()
        ? 0
        : durations.stream().mapToLong(Long::longValue).average().orElse(0);
    long p95 = 0;
    if (!durations.isEmpty()) {
      // 近似 P95：取排序后 ceil(n*0.95)-1 下标
      int idx = Math.min(durations.size() - 1, (int) Math.ceil(durations.size() * 0.95) - 1);
      p95 = durations.get(Math.max(0, idx));
    }
    // 聚合失败节点次数，供排障
    Map<String, Long> failCounts = new HashMap<>();
    if (!runs.isEmpty()) {
      for (WorkflowNodeRunEntity nr : nodeRunMapper.selectList(new LambdaQueryWrapper<WorkflowNodeRunEntity>()
          .in(WorkflowNodeRunEntity::getWorkflowRunId, runs.stream().map(WorkflowRunEntity::getId).toList())
          .eq(WorkflowNodeRunEntity::getStatus, WorkflowRunStatus.FAILED.getValue()))) {
        failCounts.merge(nr.getNodeKey(), 1L, Long::sum);
      }
    }
    List<WorkflowFailedNodeMetricView> failedNodes = failCounts.entrySet().stream()
        .sorted((left, right) -> Long.compare(right.getValue(), left.getValue()))
        .map(entry -> WorkflowFailedNodeMetricView.builder()
            .nodeKey(entry.getKey())
            .count(entry.getValue())
            .build())
        .toList();
    return WorkflowMetricsView.builder()
        .runCount(total)
        .totalRuns(total)
        .successRate(total == 0 ? 0.0 : (double) succeeded / total)
        .averageDurationSeconds(avg)
        .p95DurationSeconds(p95)
        .failedNodes(failedNodes)
        .build();
  }

  /**
   * 按节点聚合尝试次数、失败次数与平均耗时。
   *
   * @param id 工作流定义主键
   * @return 节点指标列表；无运行时返回空列表
   * @throws BusinessException 定义不存在
   */
  public List<WorkflowNodeMetricView> metricsNodes(String id) {
    support.require(id);
    List<String> runIds = runMapper.selectList(new LambdaQueryWrapper<WorkflowRunEntity>()
            .eq(WorkflowRunEntity::getWorkflowDefinitionId, id))
        .stream()
        .map(WorkflowRunEntity::getId)
        .toList();
    if (runIds.isEmpty()) {
      return List.of();
    }
    // nodeKey -> 累计 attempts/failures/durationTotal
    Map<String, Map<String, Object>> byNode = new LinkedHashMap<>();
    for (WorkflowNodeRunEntity nr : nodeRunMapper.selectList(new LambdaQueryWrapper<WorkflowNodeRunEntity>()
        .in(WorkflowNodeRunEntity::getWorkflowRunId, runIds))) {
      Map<String, Object> row = byNode.computeIfAbsent(nr.getNodeKey(), nodeKey -> {
        Map<String, Object> initial = new LinkedHashMap<>();
        initial.put("nodeKey", nodeKey);
        initial.put("attempts", 0);
        initial.put("failures", 0);
        initial.put("durationTotal", 0L);
        return initial;
      });
      row.put("attempts", ((Number) row.get("attempts")).intValue() + 1);
      if (WorkflowRunStatus.FAILED.getValue().equals(nr.getStatus())) {
        row.put("failures", ((Number) row.get("failures")).intValue() + 1);
      }
      if (nr.getDurationMs() != null) {
        row.put("durationTotal", ((Number) row.get("durationTotal")).longValue() + nr.getDurationMs());
      }
    }
    List<WorkflowNodeMetricView> out = new ArrayList<>();
    for (Map<String, Object> row : byNode.values()) {
      int attempts = ((Number) row.get("attempts")).intValue();
      long totalMs = ((Number) row.get("durationTotal")).longValue();
      out.add(WorkflowNodeMetricView.builder()
          .nodeKey(String.valueOf(row.get("nodeKey")))
          .attempts(attempts)
          .failures(((Number) row.get("failures")).intValue())
          .averageDurationMs(attempts == 0 ? 0L : totalMs / attempts)
          .build());
    }
    return out;
  }

  /**
   * 按版本（含 draft 桶）聚合运行次数、成功率与 P95 耗时。
   *
   * @param id 工作流定义主键
   * @return 版本指标列表
   * @throws BusinessException 定义不存在
   */
  public List<WorkflowVersionMetricView> metricsVersions(String id) {
    support.require(id);
    // 无 versionId 的运行归入 "draft" 桶
    Map<String, List<WorkflowRunEntity>> byVersion = new LinkedHashMap<>();
    for (WorkflowRunEntity run : runMapper.selectList(new LambdaQueryWrapper<WorkflowRunEntity>()
        .eq(WorkflowRunEntity::getWorkflowDefinitionId, id))) {
      String versionId = run.getWorkflowVersionId() == null ? "draft" : run.getWorkflowVersionId();
      byVersion.computeIfAbsent(versionId, ignored -> new ArrayList<>()).add(run);
    }
    List<WorkflowVersionMetricView> out = new ArrayList<>();
    for (Map.Entry<String, List<WorkflowRunEntity>> entry : byVersion.entrySet()) {
      List<WorkflowRunEntity> runs = entry.getValue();
      long ok = runs.stream()
          .filter(run -> WorkflowRunStatus.SUCCEEDED.getValue().equals(run.getStatus()))
          .count();
      List<Long> durations = runs.stream()
          .filter(run -> run.getCreatedAt() != null && run.getFinishedAt() != null)
          .map(run -> Duration.between(run.getCreatedAt(), run.getFinishedAt()).getSeconds())
          .sorted()
          .toList();
      long p95 = durations.isEmpty() ? 0 : durations.get(Math.min(durations.size() - 1,
          Math.max(0, (int) Math.ceil(durations.size() * 0.95) - 1)));
      // 尽量用版本号标签展示，找不到则用 id/"draft"
      String label = entry.getKey();
      WorkflowVersionEntity ver = versionMapper.selectById(entry.getKey());
      if (ver != null) {
        label = ver.getVersion();
      }
      out.add(WorkflowVersionMetricView.builder()
          .version(label)
          .runs(runs.size())
          .successRate(runs.isEmpty() ? 0.0 : (double) ok / runs.size())
          .p95DurationSeconds(p95)
          .totalTokens(0L)
          .build());
    }
    return out;
  }

  /**
   * 列出某工作流告警，按创建时间倒序。
   *
   * @param id 工作流定义主键
   * @return 告警视图列表
   * @throws BusinessException 定义不存在
   */
  public List<WorkflowAlertView> listAlerts(String id) {
    support.require(id);
    // 最新告警排前
    return alertMapper.selectList(new LambdaQueryWrapper<WorkflowAlertEntity>()
            .eq(WorkflowAlertEntity::getWorkflowId, id)
            .orderByDesc(WorkflowAlertEntity::getCreatedAt))
        .stream()
        .map(support::toAlertView)
        .toList();
  }

  /**
   * 将告警标为 resolved。
   *
   * @param workflowId 工作流定义主键（归属校验）
   * @param alertId    告警主键
   * @return 解决后的告警视图
   * @throws BusinessException 定义不存在，或告警不存在/不属于该工作流
   */
  public WorkflowAlertView resolveAlert(String workflowId, String alertId) {
    support.require(workflowId);
    WorkflowAlertEntity alert = alertMapper.selectById(alertId);
    // 必须同属该工作流，防止跨工作流误操作
    if (alert == null || !workflowId.equals(alert.getWorkflowId())) {
      throw new BusinessException(ErrorCodes.ALERT_NOT_FOUND, "告警不存在：" + alertId, 404);
    }
    alert.setStatus(WorkflowAlertStatus.RESOLVED.getValue());
    alert.setResolvedAt(LocalDateTime.now());
    alertMapper.updateById(alert);
    return support.toAlertView(alert);
  }

}
