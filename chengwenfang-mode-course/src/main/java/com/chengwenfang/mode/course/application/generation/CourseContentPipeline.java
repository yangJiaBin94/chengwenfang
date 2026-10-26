package com.chengwenfang.mode.course.application.generation;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.job.domain.entity.JobEntity;
import com.chengwenfang.job.facade.JobExecutionContext;
import com.chengwenfang.job.facade.dto.job.CourseContentGenerationJobInput;
import com.chengwenfang.job.facade.dto.job.CourseContentGenerationJobOutput;
import com.chengwenfang.job.facade.dto.job.JobPayloads;
import com.chengwenfang.mode.course.application.generation.course.CourseGraphState;
import com.chengwenfang.mode.course.application.generation.course.CourseGraphStateKeys;
import com.chengwenfang.skill.facade.WorkflowFacade;
import com.chengwenfang.skill.facade.dto.view.WorkflowGraphExecuteResult;
import com.chengwenfang.skill.facade.enums.WorkflowFeatureBinding;
import com.chengwenfang.skill.facade.enums.WorkflowRunStatus;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 课程内容生成管道：有 workflowId 时走图执行；缺省时解析已发布工作流。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseContentPipeline {
  /** 工作流图执行。 */
  private final WorkflowFacade workflows;
  /**
   * 执行课程内容生成管道。
   *
   * @param job     任务实体
   * @param context 执行上下文
   * @throws Exception 图执行失败或缺少已发布工作流
   */
  public void run(JobEntity job, JobExecutionContext context) throws Exception {
    log.info("执行管道 courseContentGeneration jobId={} entityId={}", job.getId(), job.getEntityId());
    CourseContentGenerationJobInput input = CourseContentGenerationJobInput.from(
        JobPayloads.parseMap(job.getInputJson()));
    String workflowId = stringOrNull(input.get(CourseGraphStateKeys.WORKFLOW_ID));
    if (workflowId == null || workflowId.isBlank()) {
      var published = workflows.requireActivePublished(WorkflowFeatureBinding.COURSE_CONTENT_GENERATION);
      workflowId = published.getId();
      input.put(CourseGraphStateKeys.WORKFLOW_ID, workflowId);
      if (published.getPublishedVersionId() != null) {
        input.put("workflowVersionId", published.getPublishedVersionId());
      }
    }
    step(job, context, "snapshot_context", 5, "读取并快照课程上下文");
    Map<String, Object> state = new LinkedHashMap<>(input);
    state.put(CourseGraphStateKeys.COURSE_ID, job.getEntityId());
    state.put(CourseGraphStateKeys.JOB_ID, job.getId());
    state.put(CourseGraphStateKeys.WORKFLOW_ID, workflowId);
    workflows.attachJobRun(workflowId, job.getId());
    WorkflowGraphExecuteResult result = workflows.executeGraphForJob(workflowId, job.getId(), state);
    context.renewLease(job.getId());
    // 图失败时保留节点内已上报的进度步（如 generate_wechat），勿先写 graph_finalize 以免前端误标终检
    if (WorkflowRunStatus.FAILED.getValue().equals(result.getStatus())) {
      throw new IllegalStateException(
          result.getErrorMessage() == null ? "课程成文工作流执行失败" : result.getErrorMessage());
    }
    step(job, context, "graph_finalize", 90, "汇总工作流结果");
    CourseContentGenerationJobOutput output =
        CourseContentGenerationJobOutput.fromGraph(result.toJobOutput());
    // 从终态提取 draft/wechat/images/shell，对齐顺序编排器 Job 输出
    Object stateRaw = output.get("state");
    if (stateRaw instanceof Map<?, ?> stateMap) {
      @SuppressWarnings("unchecked")
      CourseGraphState graphState = CourseGraphState.fromMap((Map<String, Object>) stateMap);
      Map<String, Object> shellOutput = graphState.toJobOutputShell();
      output.putAll(shellOutput);
    }
    output.putIfAbsent(CourseGraphStateKeys.COURSE_ID, job.getEntityId());
    output.putIfAbsent("partial", false);
    Object runId = output.get("runId");
    if (runId != null && StrUtil.isNotBlank(String.valueOf(runId))) {
      output.putIfAbsent("workflowRunId", String.valueOf(runId));
    }
    context.completeWith(output);
    step(job, context, "done", 100, "课程内容生成完成");
  }

  /**
   * 上报一步进度（已取消则跳过）；顺带续租。
   *
   * @param job      任务
   * @param context  上下文
   * @param name     步骤名
   * @param progress 进度 0–100
   * @param message  人类可读说明
   */
  private void step(JobEntity job, JobExecutionContext context, String name, int progress, String message) {
    if (context.isCancelled(job.getId())) {
      return;
    }
    context.renewLease(job.getId());
    context.progress(job.getId(), name, progress, message);
  }

  /**
   * 将任意值转为非空字符串；blank/null 字符串返回 null。
   *
   * @param value 任意对象
   * @return 字符串或 null
   */
  private static String stringOrNull(Object value) {
    if (value == null) {
      return null;
    }
    String text = String.valueOf(value);
    return text.isBlank() || "null".equals(text) ? null : text;
  }
}
