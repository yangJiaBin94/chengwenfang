package com.chengwenfang.app.adapter.web.workflow;

import com.chengwenfang.common.ApiResult;
import com.chengwenfang.skill.facade.dto.view.WorkflowAlertView;
import com.chengwenfang.skill.facade.dto.request.WorkflowCreateRequest;
import com.chengwenfang.skill.facade.dto.view.WorkflowDetailView;
import com.chengwenfang.skill.facade.dto.view.WorkflowDiffView;
import com.chengwenfang.skill.facade.dto.request.WorkflowDraftSaveRequest;
import com.chengwenfang.skill.facade.dto.view.WorkflowDraftView;
import com.chengwenfang.skill.facade.WorkflowFacade;
import com.chengwenfang.skill.facade.dto.view.WorkflowFeatureAvailabilityView;
import com.chengwenfang.skill.facade.dto.view.WorkflowMetricsView;
import com.chengwenfang.skill.facade.dto.view.WorkflowNodeMetricView;
import com.chengwenfang.skill.facade.dto.view.WorkflowNodeTypeView;
import com.chengwenfang.skill.facade.dto.request.WorkflowPublishRequest;
import com.chengwenfang.skill.facade.dto.view.WorkflowRunView;
import com.chengwenfang.skill.facade.dto.view.WorkflowSummaryView;
import com.chengwenfang.skill.facade.dto.request.WorkflowUpdateRequest;
import com.chengwenfang.skill.facade.dto.view.WorkflowValidationView;
import com.chengwenfang.skill.facade.dto.view.WorkflowVersionDiffView;
import com.chengwenfang.skill.facade.dto.view.WorkflowVersionMetricView;
import com.chengwenfang.skill.facade.dto.view.WorkflowVersionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作流定义、版本与运行相关 HTTP 接口。
 * <p>多数接口直接委托 {@link WorkflowFacade}。
 */
@Tag(name = "工作流", description = "工作流定义、草稿、发布、运行与指标")
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WorkflowController {
  /** 工作流领域门面。 */
  private final WorkflowFacade workflows;

  /**
   * 列出工作流定义摘要。
   *
   * @return 工作流摘要列表
   */
  @GetMapping("/workflows")
  @Operation(summary = "列出工作流定义")
  public ApiResult<List<WorkflowSummaryView>> workflows() {
    return ApiResult.ok(workflows.listWorkflows());
  }

  /**
   * 创建工作流（含空草稿）。
   *
   * @param body 创建请求；可为空
   * @return 新建工作流摘要
   */
  @PostMapping("/workflows")
  @Operation(summary = "创建工作流")
  public ApiResult<WorkflowSummaryView> createWorkflow(@Valid @RequestBody(required = false) WorkflowCreateRequest body) {
    return ApiResult.ok(workflows.createWorkflow(body));
  }

  /**
   * 列出可编排的节点类型目录。
   *
   * @return 节点类型视图列表
   */
  @GetMapping("/workflows/node-types")
  @Operation(summary = "列出节点类型")
  public ApiResult<List<WorkflowNodeTypeView>> workflowNodeTypes() {
    return ApiResult.ok(workflows.listNodeTypes());
  }

  /**
   * 按功能键反查绑定工作流是否可用（按钮门禁；须放在 /workflows/{id} 之前）。
   *
   * @param featureKey 功能键，如 hot_news_radar_collect
   * @return 可用性视图
   */
  @GetMapping("/workflows/features/{featureKey}")
  @Operation(summary = "反查功能绑定工作流可用性")
  public ApiResult<WorkflowFeatureAvailabilityView> workflowFeatureAvailability(
      @PathVariable String featureKey) {
    return ApiResult.ok(workflows.getFeatureAvailability(featureKey));
  }

  /**
   * 获取工作流详情（含草稿定义、校验与指标）。
   *
   * @param id 工作流定义主键
   * @return 详情视图
   */
  @GetMapping("/workflows/{id}")
  @Operation(summary = "获取工作流详情")
  public ApiResult<WorkflowDetailView> workflow(@PathVariable String id) {
    return ApiResult.ok(workflows.getWorkflow(id));
  }

  /**
   * 部分更新工作流元数据（名称、描述、状态等）。
   *
   * @param id   工作流定义主键
   * @param body 更新请求；可为空
   * @return 更新后的摘要
   */
  @PatchMapping("/workflows/{id}")
  @Operation(summary = "更新工作流元数据")
  public ApiResult<WorkflowSummaryView> patchWorkflow(
      @PathVariable String id, @Valid @RequestBody(required = false) WorkflowUpdateRequest body) {
    return ApiResult.ok(workflows.updateWorkflow(id, body));
  }

  /**
   * 获取工作流草稿。
   *
   * @param id 工作流定义主键
   * @return 草稿视图
   */
  @GetMapping("/workflows/{id}/draft")
  @Operation(summary = "获取工作流草稿")
  public ApiResult<WorkflowDraftView> workflowDraft(@PathVariable String id) {
    return ApiResult.ok(workflows.getDraft(id));
  }

  /**
   * 保存工作流草稿定义。
   *
   * @param id   工作流定义主键
   * @param body 草稿保存请求（可含 revision 做乐观锁）；可为空
   * @return 保存后的草稿视图
   */
  @PutMapping("/workflows/{id}/draft")
  @Operation(summary = "保存工作流草稿")
  public ApiResult<WorkflowDraftView> saveWorkflowDraft(
      @PathVariable String id, @Valid @RequestBody(required = false) WorkflowDraftSaveRequest body) {
    return ApiResult.ok(workflows.saveDraft(id, body));
  }

  /**
   * 校验工作流草稿图合法性（可不落库）。
   *
   * @param id   工作流定义主键
   * @param body 可选：带 definition 则校验该图，否则校验库中草稿
   * @return 校验结果视图
   */
  @PostMapping("/workflows/{id}/draft/validate")
  @Operation(summary = "校验工作流草稿")
  public ApiResult<WorkflowValidationView> validateWorkflowDraft(
      @PathVariable String id, @Valid @RequestBody(required = false) WorkflowDraftSaveRequest body) {
    return ApiResult.ok(workflows.validateDraft(id, body));
  }

  /**
   * 重置工作流草稿；若 body 带定义则等价于保存。
   *
   * @param id   工作流定义主键
   * @param body 可选重置/保存内容
   * @return 重置或保存后的草稿
   */
  @PostMapping("/workflows/{id}/draft/reset")
  @Operation(summary = "重置工作流草稿")
  public ApiResult<WorkflowDraftView> resetWorkflowDraft(
      @PathVariable String id, @Valid @RequestBody(required = false) WorkflowDraftSaveRequest body) {
    return ApiResult.ok(workflows.resetDraft(id, body));
  }

  /**
   * 草稿相对已发布版本的差异（当前实现可能返回空 changes）。
   *
   * @param id 工作流定义主键
   * @return 差异视图
   */
  @GetMapping("/workflows/{id}/draft/diff")
  @Operation(summary = "草稿相对已发布版本差异")
  public ApiResult<WorkflowDiffView> workflowDraftDiff(@PathVariable String id) {
    return ApiResult.ok(workflows.draftDiff(id));
  }

  /**
   * 发布工作流草稿为新版本。
   *
   * @param id   工作流定义主键
   * @param body 发布请求（版本号、发布说明等）；可为空
   * @return 新发布的版本视图
   */
  @PostMapping("/workflows/{id}/publish")
  @Operation(summary = "发布工作流草稿")
  public ApiResult<WorkflowVersionView> publishWorkflow(
      @PathVariable String id, @Valid @RequestBody(required = false) WorkflowPublishRequest body) {
    return ApiResult.ok(workflows.publish(id, body));
  }

  /**
   * 列出工作流已发布版本。
   *
   * @param id 工作流定义主键
   * @return 版本列表
   */
  @GetMapping("/workflows/{id}/versions")
  @Operation(summary = "列出工作流版本")
  public ApiResult<List<WorkflowVersionView>> workflowVersions(@PathVariable String id) {
    return ApiResult.ok(workflows.listVersions(id));
  }

  /**
   * 工作流两版本差异。
   *
   * @param id   工作流定义主键
   * @param from 起始版本标识（可选）
   * @param to   目标版本标识（可选）
   * @return 版本差异视图
   */
  @GetMapping("/workflows/{id}/versions/diff")
  @Operation(summary = "工作流两版本差异")
  public ApiResult<WorkflowVersionDiffView> workflowVersionDiff(
      @PathVariable String id,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to) {
    return ApiResult.ok(workflows.versionDiff(id, from, to));
  }

  /**
   * 从历史版本创建/覆盖当前草稿。
   *
   * @param id        工作流定义主键
   * @param versionId 源版本主键
   * @return 基于该版本的草稿视图
   */
  @PostMapping("/workflows/{id}/versions/{versionId}/create-draft")
  @Operation(summary = "从版本创建草稿")
  public ApiResult<WorkflowDraftView> createDraftFromVersion(
      @PathVariable String id, @PathVariable String versionId) {
    return ApiResult.ok(workflows.createDraftFromVersion(id, versionId));
  }

  /**
   * 列出工作流运行记录。
   *
   * @param id 工作流定义主键
   * @return 运行视图列表
   */
  @GetMapping("/workflows/{id}/runs")
  @Operation(summary = "列出工作流运行")
  public ApiResult<List<WorkflowRunView>> workflowRuns(@PathVariable String id) {
    return ApiResult.ok(workflows.listRuns(id));
  }

  /**
   * 工作流运行指标汇总。
   *
   * @param id 工作流定义主键
   * @return 汇总指标视图
   */
  @GetMapping("/workflows/{id}/metrics/summary")
  @Operation(summary = "工作流运行指标汇总")
  public ApiResult<WorkflowMetricsView> workflowMetricsSummary(@PathVariable String id) {
    return ApiResult.ok(workflows.metricsSummary(id));
  }

  /**
   * 按节点聚合的运行指标。
   *
   * @param id 工作流定义主键
   * @return 节点指标列表
   */
  @GetMapping("/workflows/{id}/metrics/nodes")
  @Operation(summary = "按节点的运行指标")
  public ApiResult<List<WorkflowNodeMetricView>> workflowMetricsNodes(@PathVariable String id) {
    return ApiResult.ok(workflows.metricsNodes(id));
  }

  /**
   * 按版本聚合的运行指标。
   *
   * @param id 工作流定义主键
   * @return 版本指标列表
   */
  @GetMapping("/workflows/{id}/metrics/versions")
  @Operation(summary = "按版本的运行指标")
  public ApiResult<List<WorkflowVersionMetricView>> workflowMetricsVersions(@PathVariable String id) {
    return ApiResult.ok(workflows.metricsVersions(id));
  }

  /**
   * 列出工作流告警。
   *
   * @param id 工作流定义主键
   * @return 告警视图列表
   */
  @GetMapping("/workflows/{id}/alerts")
  @Operation(summary = "列出工作流告警")
  public ApiResult<List<WorkflowAlertView>> workflowAlerts(@PathVariable String id) {
    return ApiResult.ok(workflows.listAlerts(id));
  }

  /**
   * 将指定告警标记为已解决。
   *
   * @param id      工作流定义主键（用于归属校验）
   * @param alertId 告警主键
   * @return 解决后的告警视图
   */
  @PostMapping("/workflows/{id}/alerts/{alertId}/resolve")
  @Operation(summary = "解决工作流告警")
  public ApiResult<WorkflowAlertView> resolveWorkflowAlert(
      @PathVariable String id, @PathVariable String alertId) {
    return ApiResult.ok(workflows.resolveAlert(id, alertId));
  }

}
