package com.workbench.skill.application;

import com.workbench.skill.facade.WorkflowFacade;
import com.workbench.skill.facade.dto.request.WorkflowCreateRequest;
import com.workbench.skill.facade.dto.request.WorkflowDraftSaveRequest;
import com.workbench.skill.facade.dto.request.WorkflowPublishRequest;
import com.workbench.skill.facade.dto.request.WorkflowUpdateRequest;
import com.workbench.skill.facade.dto.view.WorkflowAlertView;
import com.workbench.skill.facade.dto.view.WorkflowDefinitionDataView;
import com.workbench.skill.facade.dto.view.WorkflowDetailView;
import com.workbench.skill.facade.dto.view.WorkflowDiffView;
import com.workbench.skill.facade.dto.view.WorkflowDraftView;
import com.workbench.skill.facade.dto.view.WorkflowFeatureAvailabilityView;
import com.workbench.skill.facade.dto.view.WorkflowGraphExecuteResult;
import com.workbench.skill.facade.dto.view.WorkflowMetricsView;
import com.workbench.skill.facade.dto.view.WorkflowNodeMetricView;
import com.workbench.skill.facade.dto.view.WorkflowNodeRunView;
import com.workbench.skill.facade.dto.view.WorkflowNodeTypeView;
import com.workbench.skill.facade.dto.view.WorkflowRunView;
import com.workbench.skill.facade.dto.view.WorkflowSummaryView;
import com.workbench.skill.facade.dto.view.WorkflowValidationView;
import com.workbench.skill.facade.dto.view.WorkflowVersionDiffView;
import com.workbench.skill.facade.dto.view.WorkflowVersionMetricView;
import com.workbench.skill.facade.dto.view.WorkflowVersionView;
import com.workbench.skill.facade.enums.WorkflowFeatureBinding;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link WorkflowFacade} 默认实现：薄门面，按职责转发到领域协作服务。
 *
 * <ul>
 *   <li>{@link WorkflowDefinitionService} — 定义 CRUD；草稿/版本薄委托 Draft/Version 服务</li>
 *   <li>{@link WorkflowResolveService} — 按场景/功能解析已发布工作流与快照</li>
 *   <li>{@link WorkflowRunService} — Job 关联、节点运行与图执行（{@link com.workbench.skill.domain.runtime.GraphRuntime}）</li>
 *   <li>{@link WorkflowObservabilityService} — 指标与告警</li>
 *   <li>{@link BuiltinWorkflowDefinitionService} — 内置工作流 ensure*（复用 {@link BuiltinWorkflowSeeder}）</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class WorkflowFacadeImpl implements WorkflowFacade {

  /** 定义 CRUD（草稿/版本经其委托）。 */
  private final WorkflowDefinitionService definitions;
  /** 场景/功能反查与快照。 */
  private final WorkflowResolveService resolve;
  /** 运行、节点与图执行。 */
  private final WorkflowRunService runs;
  /** 指标与告警。 */
  private final WorkflowObservabilityService observability;
  /** 内置工作流种子。 */
  private final BuiltinWorkflowDefinitionService builtins;

  /** {@inheritDoc} */
  @Override
  public List<WorkflowSummaryView> listWorkflows() {
    return definitions.listWorkflows();
  }

  /** {@inheritDoc} */
  @Override
  public List<WorkflowNodeTypeView> listNodeTypes() {
    return definitions.listNodeTypes();
  }

  /** {@inheritDoc} */
  @Override
  public WorkflowDetailView getWorkflow(String id) {
    return definitions.getWorkflow(id);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public WorkflowSummaryView createWorkflow(WorkflowCreateRequest body) {
    return definitions.createWorkflow(body);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public WorkflowSummaryView updateWorkflow(String id, WorkflowUpdateRequest body) {
    return definitions.updateWorkflow(id, body);
  }

  /** {@inheritDoc} */
  @Override
  public WorkflowDraftView getDraft(String id) {
    return definitions.getDraft(id);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public WorkflowDraftView saveDraft(String id, WorkflowDraftSaveRequest body) {
    return definitions.saveDraft(id, body);
  }

  /** {@inheritDoc} */
  @Override
  public WorkflowValidationView validateDraft(String id, WorkflowDraftSaveRequest body) {
    return definitions.validateDraft(id, body);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public WorkflowDraftView resetDraft(String id, WorkflowDraftSaveRequest body) {
    return definitions.resetDraft(id, body);
  }

  /** {@inheritDoc} */
  @Override
  public WorkflowDiffView draftDiff(String id) {
    return definitions.draftDiff(id);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public WorkflowVersionView publish(String id, WorkflowPublishRequest body) {
    return definitions.publish(id, body);
  }

  /** {@inheritDoc} */
  @Override
  public List<WorkflowVersionView> listVersions(String id) {
    return definitions.listVersions(id);
  }

  /** {@inheritDoc} */
  @Override
  public WorkflowVersionDiffView versionDiff(String id, String from, String to) {
    return definitions.versionDiff(id, from, to);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public WorkflowDraftView createDraftFromVersion(String workflowId, String versionId) {
    return definitions.createDraftFromVersion(workflowId, versionId);
  }

  /** {@inheritDoc} */
  @Override
  public List<WorkflowRunView> listRuns(String id) {
    return runs.listRuns(id);
  }

  /** {@inheritDoc} */
  @Override
  public WorkflowMetricsView metricsSummary(String id) {
    return observability.metricsSummary(id);
  }

  /** {@inheritDoc} */
  @Override
  public List<WorkflowNodeMetricView> metricsNodes(String id) {
    return observability.metricsNodes(id);
  }

  /** {@inheritDoc} */
  @Override
  public List<WorkflowVersionMetricView> metricsVersions(String id) {
    return observability.metricsVersions(id);
  }

  /** {@inheritDoc} */
  @Override
  public List<WorkflowAlertView> listAlerts(String id) {
    return observability.listAlerts(id);
  }

  /** {@inheritDoc} */
  @Override
  public WorkflowAlertView resolveAlert(String workflowId, String alertId) {
    return observability.resolveAlert(workflowId, alertId);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public WorkflowRunView attachJobRun(String workflowId, String jobId) {
    return runs.attachJobRun(workflowId, jobId);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public void markJobRunTerminal(String jobId, String status) {
    runs.markJobRunTerminal(jobId, status);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public void markJobRunProgress(String jobId, String currentNode) {
    runs.markJobRunProgress(jobId, currentNode);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public String beginNodeRun(String jobId, String nodeKey, int sequenceNo, Map<String, Object> inputSummary) {
    return runs.beginNodeRun(jobId, nodeKey, sequenceNo, inputSummary);
  }

  /** {@inheritDoc} */
  @Override
  public List<WorkflowNodeRunView> listNodeRunsByJobId(String jobId) {
    return runs.listNodeRunsByJobId(jobId);
  }

  /** {@inheritDoc} */
  @Override
  public List<Map<String, Object>> skillSnapshotForWorkflow(String workflowId) {
    return resolve.skillSnapshotForWorkflow(workflowId);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public void completeNodeRun(
      String nodeRunId,
      boolean ok,
      Map<String, Object> outputSummary,
      String artifactType,
      String contentText,
      Map<String, Object> contentJson,
      String errorMessage) {
    runs.completeNodeRun(nodeRunId, ok, outputSummary, artifactType, contentText, contentJson, errorMessage);
  }

  /** {@inheritDoc} */
  @Override
  public String latestSucceededArtifactText(String jobId, String nodeKey, String artifactType) {
    return runs.latestSucceededArtifactText(jobId, nodeKey, artifactType);
  }

  /** {@inheritDoc} */
  @Override
  public String reusableArtifactText(
      String jobId, String nodeKey, String artifactType, Map<String, Object> inputSummary) {
    return runs.reusableArtifactText(jobId, nodeKey, artifactType, inputSummary);
  }

  /** {@inheritDoc} */
  @Override
  public WorkflowGraphExecuteResult executeGraphForJob(String workflowId, String jobId, Map<String, Object> state) {
    return runs.executeGraphForJob(workflowId, jobId, state);
  }

  /** {@inheritDoc} */
  @Override
  public WorkflowSummaryView resolvePublishedByScene(String scene) {
    return resolve.resolvePublishedByScene(scene);
  }

  /** {@inheritDoc} */
  @Override
  public WorkflowSummaryView requireActivePublished(WorkflowFeatureBinding binding) {
    return resolve.requireActivePublished(binding);
  }

  /** {@inheritDoc} */
  @Override
  public WorkflowFeatureAvailabilityView getFeatureAvailability(String featureKey) {
    return resolve.getFeatureAvailability(featureKey);
  }

  /** {@inheritDoc} */
  @Override
  public WorkflowDefinitionDataView loadPublishedSnapshot(String workflowId) {
    return resolve.loadPublishedSnapshot(workflowId);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public void ensureBuiltin(String key, String name, String scene) {
    builtins.ensureBuiltin(key, name, scene);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public void ensureHotNewsArticleBuiltin(Map<String, String> skillIdsByName) {
    builtins.ensureHotNewsArticleBuiltin(skillIdsByName);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public void ensureCourseContentBuiltin(Map<String, String> skillIdsByName) {
    builtins.ensureCourseContentBuiltin(skillIdsByName);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public void ensureHotNewsRadarCollectBuiltin(Map<String, String> skillIdsByName) {
    builtins.ensureHotNewsRadarCollectBuiltin(skillIdsByName);
  }

  /**
   * 导出课程规范主流程图（兼容旧调用点）。
   *
   * @param skillIdsByName Skill 名 → id
   * @return 定义 Map
   */
  public static Map<String, Object> exportCourseContentDefinition(Map<String, String> skillIdsByName) {
    return BuiltinWorkflowDefinitionService.exportCourseContentDefinition(skillIdsByName);
  }

  /**
   * 导出热点成文规范图（兼容旧调用点）。
   *
   * @param skillIdsByName Skill 名 → id
   * @return 定义 Map
   */
  public static Map<String, Object> exportHotNewsArticleDefinition(Map<String, String> skillIdsByName) {
    return BuiltinWorkflowDefinitionService.exportHotNewsArticleDefinition(skillIdsByName);
  }

  /**
   * 导出雷达采集规范图（兼容旧调用点）。
   *
   * @param skillIdsByName Skill 名 → id
   * @return 定义 Map
   */
  public static Map<String, Object> exportRadarCollectDefinition(Map<String, String> skillIdsByName) {
    return BuiltinWorkflowDefinitionService.exportRadarCollectDefinition(skillIdsByName);
  }
}
