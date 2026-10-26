package com.workbench.mode.hotnews.application.generation;

import cn.hutool.core.util.StrUtil;
import com.workbench.job.domain.entity.JobEntity;
import com.workbench.job.facade.JobExecutionContext;
import com.workbench.job.facade.dto.job.HotNewsFetchJobInput;
import com.workbench.job.facade.dto.job.HotNewsFetchJobOutput;
import com.workbench.job.facade.dto.job.JobPayloads;
import com.workbench.mode.hotnews.application.generation.radarcollect.RadarCollectGraphStateKeys;
import com.workbench.mode.hotnews.application.generation.radarcollect.RadarCollectSessionStore;
import com.workbench.mode.hotnews.facade.HotNewsFacade;
import com.workbench.skill.facade.WorkflowFacade;
import com.workbench.skill.facade.dto.view.WorkflowGraphExecuteResult;
import com.workbench.skill.facade.enums.WorkflowFeatureBinding;
import com.workbench.skill.facade.enums.WorkflowRunStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 热点雷达采集 FormalGraph 管道。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RadarCollectPipeline {
  /** 热点资讯门面。 */
  private final HotNewsFacade hotNews;
  /** 工作流图执行。 */
  private final WorkflowFacade workflows;
  /** 雷达采集会话（失败时清理）。 */
  private final RadarCollectSessionStore radarCollectSessionStore;

  /**
   * 执行热点雷达采集管道。
   *
   * @param job     任务实体
   * @param context 执行上下文
   * @throws Exception 图执行失败
   */
  public void run(JobEntity job, JobExecutionContext context) throws Exception {
    log.info("执行管道 hotNewsRadarCollect jobId={} entityId={}", job.getId(), job.getEntityId());
    HotNewsFetchJobInput input =
        HotNewsFetchJobInput.from(JobPayloads.parseMap(job.getInputJson()));
    String themeId = StrUtil.firstNonBlank(
        stringOrNull(input.get(RadarCollectGraphStateKeys.THEME_ID)),
        stringOrNull(input.get("themeId")));
    String fetchRunId = StrUtil.firstNonBlank(
        stringOrNull(input.get(RadarCollectGraphStateKeys.FETCH_RUN_ID)),
        stringOrNull(job.getEntityId()));
    String workflowId = stringOrNull(input.get("workflowId"));
    if (workflowId == null || workflowId.isBlank()) {
      var published = workflows.requireActivePublished(WorkflowFeatureBinding.HOT_NEWS_RADAR_COLLECT);
      workflowId = published.getId();
      input.put("workflowId", workflowId);
      if (published.getPublishedVersionId() != null) {
        input.put("workflowVersionId", published.getPublishedVersionId());
      }
    }
    step(job, context, "graph_execute", 15, "执行雷达采集工作流");
    Map<String, Object> state = new LinkedHashMap<>(input);
    state.put(RadarCollectGraphStateKeys.THEME_ID, themeId);
    state.put(RadarCollectGraphStateKeys.FETCH_RUN_ID, fetchRunId);
    state.put(RadarCollectGraphStateKeys.JOB_ID, job.getId());
    state.put("jobId", job.getId());
    workflows.attachJobRun(workflowId, job.getId());
    // 供长节点（渠道拉取/视频解析）在图内续租，避免 worker-lease 过期被重认领
    state.put(RadarCollectGraphStateKeys.LEASE_RENEWAL, (Runnable) () -> context.renewLease(job.getId()));
    try {
      WorkflowGraphExecuteResult result = workflows.executeGraphForJob(workflowId, job.getId(), state);
      context.renewLease(job.getId());
      if (WorkflowRunStatus.FAILED.getValue().equals(result.getStatus())) {
        throw new IllegalStateException(
            result.getErrorMessage() == null ? "雷达采集工作流执行失败" : result.getErrorMessage());
      }
      step(job, context, "graph_finalize", 90, "汇总采集结果");
      HotNewsFetchJobOutput output = HotNewsFetchJobOutput.fromGraph(result.toJobOutput());
      output.put(RadarCollectGraphStateKeys.THEME_ID, themeId);
      output.put(RadarCollectGraphStateKeys.FETCH_RUN_ID, fetchRunId);
      Object runId = output.get("runId");
      if (runId != null && StrUtil.isNotBlank(String.valueOf(runId))) {
        output.putIfAbsent("workflowRunId", String.valueOf(runId));
      }
      context.completeWith(output);
      step(job, context, "done", 100, "雷达采集完成");
    } catch (Exception exception) {
      // 图中途失败时尽量关闭 fetch_run，避免列表长期显示 pending
      try {
        if (StrUtil.isNotBlank(fetchRunId)) {
          hotNews.finishRun(
              fetchRunId,
              0,
              0,
              0,
              List.of(),
              exception.getMessage() == null ? "雷达采集失败" : exception.getMessage());
        }
      } catch (Exception finishError) {
        log.warn("雷达采集失败后关闭 fetch_run 失败 fetchRunId={} err={}",
            fetchRunId, finishError.getMessage());
      }
      radarCollectSessionStore.clear(job.getId());
      throw exception;
    }
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
