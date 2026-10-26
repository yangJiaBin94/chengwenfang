package com.workbench.skill.facade;


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

/** 工作流门面：定义/草稿/发布、运行与 Job 关联、图执行与节点产物。 */
public interface WorkflowFacade {
  /** 列出工作流定义。 */
  List<WorkflowSummaryView> listWorkflows();

  /** 列出节点类型目录。 */
  List<WorkflowNodeTypeView> listNodeTypes();

  /** 获取工作流详情。 */
  WorkflowDetailView getWorkflow(String id);

  /** 创建工作流。 */
  WorkflowSummaryView createWorkflow(WorkflowCreateRequest body);

  /** 更新工作流元数据。 */
  WorkflowSummaryView updateWorkflow(String id, WorkflowUpdateRequest body);

  /** 获取草稿。 */
  WorkflowDraftView getDraft(String id);

  /** 保存草稿定义。 */
  WorkflowDraftView saveDraft(String id, WorkflowDraftSaveRequest body);

  /** 校验草稿图合法性。 */
  WorkflowValidationView validateDraft(String id, WorkflowDraftSaveRequest body);

  /** 重置草稿。 */
  WorkflowDraftView resetDraft(String id, WorkflowDraftSaveRequest body);

  /** 草稿相对已发布版本的差异。 */
  WorkflowDiffView draftDiff(String id);

  /** 发布草稿为新版本。 */
  WorkflowVersionView publish(String id, WorkflowPublishRequest body);

  /** 列出已发布版本。 */
  List<WorkflowVersionView> listVersions(String id);

  /** 两版本差异。 */
  WorkflowVersionDiffView versionDiff(String id, String from, String to);

  /** 从历史版本创建新草稿。 */
  WorkflowDraftView createDraftFromVersion(String workflowId, String versionId);

  /** 列出运行记录。 */
  List<WorkflowRunView> listRuns(String id);

  /** 运行指标汇总。 */
  WorkflowMetricsView metricsSummary(String id);

  /** 按节点的指标。 */
  List<WorkflowNodeMetricView> metricsNodes(String id);

  /** 按版本的指标。 */
  List<WorkflowVersionMetricView> metricsVersions(String id);

  /** 列出告警。 */
  List<WorkflowAlertView> listAlerts(String id);

  /** 解决告警。 */
  WorkflowAlertView resolveAlert(String workflowId, String alertId);

  /** 为已有 Job 持久化 workflow_runs。 */
  WorkflowRunView attachJobRun(String workflowId, String jobId);

  /** Job 结束时将关联 workflow_runs 标为终态。 */
  void markJobRunTerminal(String jobId, String status);

  /** 将关联运行标为进行中，并可更新当前节点。 */
  void markJobRunProgress(String jobId, String currentNode);

  /** 为编排器正式步骤开启一条 workflow_node_runs。 */
  String beginNodeRun(String jobId, String nodeKey, int sequenceNo, Map<String, Object> inputSummary);

  /**
   * 按 Job 列出关联工作流运行的全部节点尝试（对齐原版 GET /jobs/{id}/nodes）。
   *
   * @param jobId 任务 id
   * @return 按 sequenceNo 排序的节点列表；无运行时为空列表
   */
  List<WorkflowNodeRunView> listNodeRunsByJobId(String jobId);

  /**
   * 汇总已发布工作流版本上绑定的 Skill，供任务入队写入 skillSnapshot。
   *
   * @param workflowId 工作流定义 id
   * @return 去重后的 Skill 摘要（id/name/scope/priority/nodeKeys）
   */
  List<Map<String, Object>> skillSnapshotForWorkflow(String workflowId);

  /** 完成节点运行并可选写入产物。 */
  void completeNodeRun(
      String nodeRunId,
      boolean ok,
      Map<String, Object> outputSummary,
      String artifactType,
      String contentText,
      Map<String, Object> contentJson,
      String errorMessage);

  /**
   * 取该 Job 下某节点最近一次成功产物的文本。
   *
   * @param jobId        任务主键
   * @param nodeKey      节点键
   * @param artifactType 产物类型
   * @return 产物正文；没有则 null
   */
  String latestSucceededArtifactText(String jobId, String nodeKey, String artifactType);

  /**
   * 取成功产物且节点 inputHash 与给定 inputSummary 一致时才返回文本。
   *
   * @param jobId         任务主键
   * @param nodeKey       节点键
   * @param artifactType  产物类型
   * @param inputSummary  本次输入摘要；null 时退化为 {@link #latestSucceededArtifactText}
   * @return 产物正文；没有或哈希不一致时 null
   */
  String reusableArtifactText(
      String jobId, String nodeKey, String artifactType, Map<String, Object> inputSummary);

  /** 对已关联的 Job/运行执行工作流图。 */
  WorkflowGraphExecuteResult executeGraphForJob(String workflowId, String jobId, Map<String, Object> state);

  /**
   * 按场景解析已发布且启用的工作流；课程生成场景优先匹配 key {@code course-content-generation}。
   * <p>停用 / 未发布时抛 {@code workflow_unavailable}。
   */
  WorkflowSummaryView resolvePublishedByScene(String scene);

  /**
   * 按功能绑定反查已发布且启用的工作流。
   *
   * @param binding 功能 ↔ 工作流绑定
   * @return 可用工作流摘要
   */
  WorkflowSummaryView requireActivePublished(WorkflowFeatureBinding binding);

  /**
   * 按功能键反查工作流可用性（不抛错，供按钮门禁）。
   *
   * @param featureKey 功能键
   * @return 可用性视图
   */
  WorkflowFeatureAvailabilityView getFeatureAvailability(String featureKey);

  /** 加载工作流已发布版本的图快照。 */
  WorkflowDefinitionDataView loadPublishedSnapshot(String workflowId);

  /** 确保内置工作流定义存在（启动种子）。 */
  void ensureBuiltin(String key, String name, String scene);

  /**
   * 确保热点成文内置工作流为当前骨架优先主流程（节点集不一致时覆盖并 publish）。
   *
   * @param skillIdsByName Skill 名称 → id，用于节点 skillBindings
   */
  void ensureHotNewsArticleBuiltin(Map<String, String> skillIdsByName);

  /**
   * 确保课程成文内置工作流为当前 14 节点主流程（节点集不一致时覆盖并 publish）。
   *
   * @param skillIdsByName Skill 名称 → id，用于节点 skillBindings
   */
  void ensureCourseContentBuiltin(Map<String, String> skillIdsByName);

  /**
   * 确保热点雷达采集内置工作流为当前主流程（节点集不一致时覆盖并 publish）。
   *
   * @param skillIdsByName Skill 名称 → id，用于节点 skillBindings
   */
  void ensureHotNewsRadarCollectBuiltin(Map<String, String> skillIdsByName);
}
