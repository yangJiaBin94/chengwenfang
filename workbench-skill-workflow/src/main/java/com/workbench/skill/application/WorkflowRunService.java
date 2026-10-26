package com.workbench.skill.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workbench.common.BusinessException;
import com.workbench.common.ErrorCodes;
import com.workbench.common.IdsUtil;
import com.workbench.file.facade.FileFacade;
import com.workbench.file.facade.dto.view.StoredFileView;
import com.workbench.skill.domain.entity.WorkflowAlertEntity;
import com.workbench.skill.domain.entity.WorkflowArtifactEntity;
import com.workbench.skill.facade.constant.WorkflowArtifactContentFormats;
import com.workbench.skill.domain.entity.WorkflowDefinitionEntity;
import com.workbench.skill.domain.entity.WorkflowDraftEntity;
import com.workbench.skill.domain.entity.WorkflowNodeRunEntity;
import com.workbench.skill.domain.entity.WorkflowRunEntity;
import com.workbench.skill.domain.entity.WorkflowVersionEntity;
import com.workbench.skill.domain.runtime.GraphRuntime;
import com.workbench.skill.facade.enums.WorkflowAlertMetric;
import com.workbench.skill.facade.enums.WorkflowAlertSeverity;
import com.workbench.skill.facade.enums.WorkflowAlertStatus;
import com.workbench.skill.facade.enums.WorkflowRunStatus;
import com.workbench.skill.facade.dto.view.WorkflowGraphExecuteResult;
import com.workbench.skill.facade.dto.view.WorkflowNodeRunView;
import com.workbench.skill.facade.dto.view.WorkflowRunView;
import com.workbench.skill.infrastructure.mapper.WorkflowAlertMapper;
import com.workbench.skill.infrastructure.mapper.WorkflowArtifactMapper;
import com.workbench.skill.infrastructure.mapper.WorkflowNodeRunMapper;
import com.workbench.skill.infrastructure.mapper.WorkflowRunMapper;
import com.workbench.skill.infrastructure.mapper.WorkflowVersionMapper;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 工作流运行：Job 关联、节点尝试/产物与图执行（委托 GraphRuntime）。
 * <p>由 WorkflowFacadeImpl 薄委托，不对外暴露。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowRunService {

  /** 工作流运行表。 */
  private final WorkflowRunMapper runMapper;
  /** 节点运行表。 */
  private final WorkflowNodeRunMapper nodeRunMapper;
  /** 节点产物表。 */
  private final WorkflowArtifactMapper artifactMapper;
  /** 告警表（图失败时写入）。 */
  private final WorkflowAlertMapper alertMapper;
  /** 版本快照（图执行优先已发布图）。 */
  private final WorkflowVersionMapper versionMapper;
  /** JSON 工具。 */
  private final ObjectMapper objectMapper;
  /** 图干跑与正式执行引擎。 */
  private final GraphRuntime graphRuntime;
  /** 共享工具。 */
  private final WorkflowSupport support;
  /** 产物正文存文件。 */
  private final FileFacade files;

  /**
   * 列出某工作流的运行记录，按创建时间倒序。
   *
   * @param id 工作流定义主键
   * @return 运行视图列表
   * @throws BusinessException 定义不存在
   */
  public List<WorkflowRunView> listRuns(String id) {
    support.require(id);
    // 最近运行排前
    return runMapper.selectList(new LambdaQueryWrapper<WorkflowRunEntity>()
            .eq(WorkflowRunEntity::getWorkflowDefinitionId, id)
            .orderByDesc(WorkflowRunEntity::getCreatedAt))
        .stream()
        .map(support::toRunView)
        .toList();
  }
  /**
   * 为已有 Job 持久化一条 {@code workflow_runs}；同 jobId 幂等返回已有行。
   *
   * @param workflowId 工作流定义主键
   * @param jobId      后台任务主键（必填）
   * @return 新建或已存在的运行视图
   * @throws BusinessException 定义不存在或 jobId 缺失
   */
  @Transactional
  public WorkflowRunView attachJobRun(String workflowId, String jobId) {
    WorkflowDefinitionEntity def = support.require(workflowId);
    if (jobId == null || jobId.isBlank()) {
      throw new BusinessException(ErrorCodes.JOB_REQUIRED, "关联工作流运行需要 jobId", 400);
    }
    log.info("工作流关联任务运行 workflowId={} jobId={}", workflowId, jobId);
    // 同 job 只建一条 run，重复调用直接回显
    WorkflowRunEntity existing = runMapper.selectOne(new LambdaQueryWrapper<WorkflowRunEntity>()
        .eq(WorkflowRunEntity::getJobId, jobId)
        .last("LIMIT 1"));
    if (existing != null) {
      return support.toRunView(existing);
    }
    LocalDateTime now = LocalDateTime.now();
    WorkflowRunEntity run = new WorkflowRunEntity();
    run.setId(IdsUtil.newId());
    run.setJobId(jobId);
    run.setWorkflowName(def.getName() == null ? def.getWorkflowKey() : def.getName());
    run.setThreadId(null);
    run.setStatus(WorkflowRunStatus.QUEUED.getValue());
    run.setCurrentNode(null);
    run.setWorkflowDefinitionId(def.getId());
    // 关联当前已发布版本（若有），便于指标按版本聚合
    run.setWorkflowVersionId(def.getPublishedVersionId());
    run.setCreatedAt(now);
    run.setUpdatedAt(now);
    run.setFinishedAt(null);
    runMapper.insert(run);
    return support.toRunView(run);
  }

  /**
   * Job 结束时将关联 {@code workflow_runs} 标为终态（写 finishedAt）。
   *
   * @param jobId  任务主键；空白则忽略
   * @param status 终态状态码；空白默认 failed
   */
  @Transactional
  public void markJobRunTerminal(String jobId, String status) {
    if (jobId == null || jobId.isBlank()) {
      return;
    }
    WorkflowRunEntity run = runMapper.selectOne(new LambdaQueryWrapper<WorkflowRunEntity>()
        .eq(WorkflowRunEntity::getJobId, jobId)
        .last("LIMIT 1"));
    if (run == null) {
      return;
    }
    // 已终态则不再改写，避免覆盖
    if (run.getFinishedAt() != null) {
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    run.setStatus(status == null || status.isBlank()
        ? WorkflowRunStatus.FAILED.getValue()
        : status);
    run.setUpdatedAt(now);
    run.setFinishedAt(now);
    runMapper.updateById(run);
  }

  /**
   * 将关联运行标为进行中，并可更新当前节点；已终态则忽略。
   *
   * @param jobId       任务主键；空白则忽略
   * @param currentNode 当前执行节点键，可为 null
   */
  @Transactional
  public void markJobRunProgress(String jobId, String currentNode) {
    if (jobId == null || jobId.isBlank()) {
      return;
    }
    WorkflowRunEntity run = runMapper.selectOne(new LambdaQueryWrapper<WorkflowRunEntity>()
        .eq(WorkflowRunEntity::getJobId, jobId)
        .last("LIMIT 1"));
    if (run == null || run.getFinishedAt() != null) {
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    // 非终态且非 running 时推进到 running
    if (!WorkflowRunStatus.RUNNING.getValue().equals(run.getStatus())
        && !WorkflowRunStatus.SUCCEEDED.getValue().equals(run.getStatus())
        && !WorkflowRunStatus.FAILED.getValue().equals(run.getStatus())) {
      run.setStatus(WorkflowRunStatus.RUNNING.getValue());
    }
    run.setCurrentNode(currentNode);
    run.setUpdatedAt(now);
    runMapper.updateById(run);
  }

  /**
   * 为编排器正式步骤开启一条 {@code workflow_node_runs}，并同步父 run 为 running。
   *
   * @param jobId         任务主键（定位父 run）
   * @param nodeKey       节点键
   * @param sequenceNo    在本次图执行中的序号
   * @param inputSummary  输入摘要（可 null），用于 hash 与落库
   * @return 新建节点运行主键；找不到父 run 时返回 null
   */
  @Transactional
  public String beginNodeRun(String jobId, String nodeKey, int sequenceNo, Map<String, Object> inputSummary) {
    WorkflowRunEntity run = runMapper.selectOne(new LambdaQueryWrapper<WorkflowRunEntity>()
        .eq(WorkflowRunEntity::getJobId, jobId)
        .last("LIMIT 1"));
    if (run == null) {
      return null;
    }
    LocalDateTime now = LocalDateTime.now();
    if (!WorkflowRunStatus.RUNNING.getValue().equals(run.getStatus())) {
      run.setStatus(WorkflowRunStatus.RUNNING.getValue());
    }
    run.setCurrentNode(nodeKey);
    run.setUpdatedAt(now);
    run.setFinishedAt(null);
    runMapper.updateById(run);

    WorkflowNodeRunEntity entity = new WorkflowNodeRunEntity();
    entity.setId(IdsUtil.newId());
    entity.setWorkflowRunId(run.getId());
    entity.setNodeKey(nodeKey);
    entity.setSequenceNo(sequenceNo);
    entity.setAttemptNo(1);
    entity.setStatus(WorkflowRunStatus.RUNNING.getValue());
    // 输入摘要哈希便于幂等/对比
    entity.setInputHash(support.hashJson(inputSummary));
    entity.setInputSummary(support.writeJson(inputSummary == null ? Map.of() : inputSummary));
    entity.setExecutorVersion("formal-1.0");
    entity.setConfigSnapshot("{}");
    entity.setStartedAt(now);
    entity.setHeartbeatAt(now);
    entity.setCreatedAt(now);
    nodeRunMapper.insert(entity);
    return entity.getId();
  }

  /**
   * 按 Job 列出关联运行的全部节点尝试（对齐原版 GET /jobs/{id}/nodes）。
   */
  public List<WorkflowNodeRunView> listNodeRunsByJobId(String jobId) {
    if (jobId == null || jobId.isBlank()) {
      return List.of();
    }
    WorkflowRunEntity run = runMapper.selectOne(new LambdaQueryWrapper<WorkflowRunEntity>()
        .eq(WorkflowRunEntity::getJobId, jobId)
        .last("LIMIT 1"));
    if (run == null) {
      return List.of();
    }
    return nodeRunMapper.selectList(new LambdaQueryWrapper<WorkflowNodeRunEntity>()
            .eq(WorkflowNodeRunEntity::getWorkflowRunId, run.getId())
            .orderByAsc(WorkflowNodeRunEntity::getSequenceNo)
            .orderByAsc(WorkflowNodeRunEntity::getStartedAt))
        .stream()
        .map(support::toNodeRunView)
        .toList();
  }

  /**
   * 取该 Job 下某节点最近一次成功产物文本，供失败重试复用。
   */
  public String latestSucceededArtifactText(String jobId, String nodeKey, String artifactType) {
    return findSucceededArtifactText(jobId, nodeKey, artifactType, null);
  }

  /**
   * 仅当成功节点的 inputHash 与本次 inputSummary 一致时才复用产物。
   */
  public String reusableArtifactText(
      String jobId, String nodeKey, String artifactType, Map<String, Object> inputSummary) {
    if (inputSummary == null) {
      return latestSucceededArtifactText(jobId, nodeKey, artifactType);
    }
    return findSucceededArtifactText(jobId, nodeKey, artifactType, support.hashJson(inputSummary));
  }

  private String findSucceededArtifactText(
      String jobId, String nodeKey, String artifactType, String requiredInputHash) {
    if (jobId == null || jobId.isBlank() || nodeKey == null || nodeKey.isBlank()
        || artifactType == null || artifactType.isBlank()) {
      return null;
    }
    List<WorkflowRunEntity> runs = runMapper.selectList(new LambdaQueryWrapper<WorkflowRunEntity>()
        .eq(WorkflowRunEntity::getJobId, jobId));
    if (runs.isEmpty()) {
      return null;
    }
    List<String> runIds = runs.stream().map(WorkflowRunEntity::getId).toList();
    List<WorkflowNodeRunEntity> nodes = nodeRunMapper.selectList(new LambdaQueryWrapper<WorkflowNodeRunEntity>()
        .in(WorkflowNodeRunEntity::getWorkflowRunId, runIds)
        .eq(WorkflowNodeRunEntity::getNodeKey, nodeKey)
        .eq(WorkflowNodeRunEntity::getStatus, WorkflowRunStatus.SUCCEEDED.getValue())
        .orderByDesc(WorkflowNodeRunEntity::getCreatedAt));
    for (WorkflowNodeRunEntity node : nodes) {
      if (requiredInputHash != null) {
        String nodeHash = node.getInputHash() == null ? "" : node.getInputHash();
        if (!requiredInputHash.equals(nodeHash)) {
          continue;
        }
      }
      WorkflowArtifactEntity artifact = artifactMapper.selectOne(new LambdaQueryWrapper<WorkflowArtifactEntity>()
          .eq(WorkflowArtifactEntity::getNodeRunId, node.getId())
          .eq(WorkflowArtifactEntity::getArtifactType, artifactType)
          .isNotNull(WorkflowArtifactEntity::getContentFileId)
          .orderByDesc(WorkflowArtifactEntity::getCreatedAt)
          .last("LIMIT 1"));
      if (artifact == null || StrUtil.isBlank(artifact.getContentFileId())) {
        continue;
      }
      // 正文已迁到 stored_files，按 fileId 原样读出
      String text = files.readText(artifact.getContentFileId());
      if (StrUtil.isNotBlank(text)) {
        return text;
      }
    }
    return null;
  }

  /**
   * 完成节点运行：写终态/耗时/输出摘要；成功且指定 artifactType 时落产物。
   *
   * @param nodeRunId     节点运行主键；空白则忽略
   * @param ok            是否成功
   * @param outputSummary 输出摘要（可 null）
   * @param artifactType  产物类型；成功且非空时写入 artifacts
   * @param contentText   产物文本内容（可选）
   * @param contentJson   产物 JSON（可选）；缺省则用 outputSummary
   * @param errorMessage  失败时的错误信息
   */
  @Transactional
  public void completeNodeRun(
      String nodeRunId,
      boolean ok,
      Map<String, Object> outputSummary,
      String artifactType,
      String contentText,
      Map<String, Object> contentJson,
      String errorMessage) {
    if (nodeRunId == null || nodeRunId.isBlank()) {
      return;
    }
    WorkflowNodeRunEntity entity = nodeRunMapper.selectById(nodeRunId);
    if (entity == null) {
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    entity.setStatus(ok ? WorkflowRunStatus.SUCCEEDED.getValue() : WorkflowRunStatus.FAILED.getValue());
    entity.setOutputSummary(support.writeJson(outputSummary == null ? Map.of() : outputSummary));
    entity.setOutputHash(support.hashJson(outputSummary));
    entity.setErrorMessage(ok ? null : errorMessage);
    entity.setErrorCode(ok ? null : "NODE_FAILED");
    entity.setFinishedAt(now);
    // 由 startedAt 推算毫秒耗时
    if (entity.getStartedAt() != null) {
      entity.setDurationMs(ChronoUnit.MILLIS.between(entity.getStartedAt(), now));
    }
    nodeRunMapper.updateById(entity);
    // 成功路径可选落产物，供后续审核/导出
    if (ok && artifactType != null && !artifactType.isBlank()) {
      String format;
      String body;
      if (contentText != null) {
        format = WorkflowArtifactContentFormats.TEXT;
        body = contentText;
      } else if (contentJson != null) {
        format = WorkflowArtifactContentFormats.JSON;
        body = support.writeJson(contentJson);
      } else if (outputSummary != null) {
        format = WorkflowArtifactContentFormats.JSON;
        body = support.writeJson(outputSummary);
      } else {
        format = WorkflowArtifactContentFormats.TEXT;
        body = "";
      }
      String objectKey = "workflow-artifacts/" + entity.getWorkflowRunId() + "/" + IdsUtil.newId() + ".txt";
      StoredFileView stored = files.writeText(objectKey, artifactType + ".txt", body);
      WorkflowArtifactEntity art = new WorkflowArtifactEntity();
      art.setId(IdsUtil.newId());
      art.setWorkflowRunId(entity.getWorkflowRunId());
      art.setNodeRunId(entity.getId());
      art.setArtifactType(artifactType);
      art.setContentFormat(format);
      art.setContentFileId(stored.getId());
      art.setContentHash(entity.getOutputHash());
      art.setSchemaVersion("1");
      art.setCreatedAt(now);
      artifactMapper.insert(art);
    }
  }
  /**
   * 对已关联的 Job/运行执行工作流图；失败写告警。
   *
   * @param workflowId 工作流定义主键
   * @param jobId      任务主键
   * @param state      执行状态/上下文
   * @return 图执行结果（status、errorMessage、完整 payload）
   * @throws BusinessException 定义不存在或图执行抛错
   */
  public WorkflowGraphExecuteResult executeGraphForJob(String workflowId, String jobId, Map<String, Object> state) {
    WorkflowDefinitionEntity def = support.require(workflowId);
    // 停用工作流不得继续跑图（防止入参已带 workflowId 绕过反查）
    support.assertDefinitionRunnable(def);
    // 确保 job 已关联 workflow_run
    WorkflowRunEntity run = runMapper.selectOne(new LambdaQueryWrapper<WorkflowRunEntity>()
        .eq(WorkflowRunEntity::getJobId, jobId)
        .last("LIMIT 1"));
    if (run == null) {
      attachJobRun(workflowId, jobId);
      run = runMapper.selectOne(new LambdaQueryWrapper<WorkflowRunEntity>()
          .eq(WorkflowRunEntity::getJobId, jobId)
          .last("LIMIT 1"));
    }
    // 优先用已发布快照，否则回退草稿图
    WorkflowDraftEntity draft = support.findDraft(workflowId);
    Map<String, Object> definition = draft == null
        ? WorkflowSupport.emptyDefinition(def)
        : support.readDefinition(draft.getDefinitionJson(), def);
    if (def.getPublishedVersionId() != null) {
      WorkflowVersionEntity published = versionMapper.selectById(def.getPublishedVersionId());
      if (published != null && published.getSnapshotJson() != null) {
        try {
          definition = objectMapper.readValue(published.getSnapshotJson(), new TypeReference<>() {});
        } catch (Exception ignored) {
          // 解析失败则保留草稿定义
        }
      }
    }
    try {
      Map<String, Object> result = graphRuntime.execute(run, definition, state);
      if (WorkflowRunStatus.FAILED.getValue().equals(result.get("status"))) {
        createFailureAlert(workflowId, run.getId(), String.valueOf(result.getOrDefault("errorMessage", "graph failed")));
      }
      return WorkflowGraphExecuteResult.builder()
          .status(result.get("status") == null ? null : String.valueOf(result.get("status")))
          .errorMessage(result.get("errorMessage") == null ? null : String.valueOf(result.get("errorMessage")))
          .payload(result)
          .build();
    } catch (Exception exception) {
      createFailureAlert(workflowId, run.getId(), exception.getMessage());
      throw new BusinessException("workflow_graph_failed", exception.getMessage(), 500);
    }
  }

  /**
   * 写入一条 run_failed 告警（open 状态）。
   *
   * @param workflowId 工作流定义主键
   * @param runId      关联运行主键
   * @param message    告警正文；null 记为 unknown
   */
  private void createFailureAlert(String workflowId, String runId, String message) {
    LocalDateTime now = LocalDateTime.now();
    // 固定 metric/severity，便于列表过滤失败类告警
    WorkflowAlertEntity alert = new WorkflowAlertEntity();
    alert.setId(IdsUtil.newId());
    alert.setWorkflowId(workflowId);
    alert.setRunId(runId);
    alert.setMetric(WorkflowAlertMetric.RUN_FAILED.getValue());
    alert.setSeverity(WorkflowAlertSeverity.ERROR.getValue());
    alert.setTitle("工作流运行失败");
    alert.setMessage(message == null ? "unknown" : message);
    alert.setStatus(WorkflowAlertStatus.OPEN.getValue());
    alert.setCreatedAt(now);
    alertMapper.insert(alert);
  }

}
