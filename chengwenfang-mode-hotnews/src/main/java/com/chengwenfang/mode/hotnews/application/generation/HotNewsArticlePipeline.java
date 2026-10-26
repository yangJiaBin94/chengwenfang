package com.chengwenfang.mode.hotnews.application.generation;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.job.domain.entity.JobEntity;
import com.chengwenfang.job.facade.JobExecutionContext;
import com.chengwenfang.job.facade.dto.job.HotNewsArticleGenerationJobInput;
import com.chengwenfang.job.facade.dto.job.HotNewsArticleGenerationJobOutput;
import com.chengwenfang.job.facade.dto.job.JobPayloads;
import com.chengwenfang.skill.facade.WorkflowFacade;
import com.chengwenfang.skill.facade.dto.view.WorkflowGraphExecuteResult;
import com.chengwenfang.skill.facade.enums.WorkflowFeatureBinding;
import com.chengwenfang.skill.facade.enums.WorkflowRunStatus;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 热点文章生成管道（大纲 → 原稿 → 公众号 → 落库）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotNewsArticlePipeline {
  /** 工作流图执行。 */
  private final WorkflowFacade workflows;
  /**
   * 执行热点文章生成管道。
   *
   * @param job     任务实体
   * @param context 执行上下文
   * @throws Exception articleId 缺失或生成失败
   */
  public void run(JobEntity job, JobExecutionContext context) throws Exception {
    log.info("执行管道 hotNewsArticleGeneration jobId={} entityId={}", job.getId(), job.getEntityId());
    HotNewsArticleGenerationJobInput input = HotNewsArticleGenerationJobInput.from(
        JobPayloads.parseMap(job.getInputJson()));
    String articleId = StrUtil.firstNonBlank(stringOrNull(job.getEntityId()), stringOrNull(input.get("articleId")));
    if (articleId == null) {
      throw new IllegalArgumentException("articleId 不能为空");
    }
    String workflowId = stringOrNull(input.get("workflowId"));
    if (workflowId == null || workflowId.isBlank()) {
      var published = workflows.requireActivePublished(WorkflowFeatureBinding.HOT_NEWS_ARTICLE_GENERATION);
      workflowId = published.getId();
      input.put("workflowId", workflowId);
      if (published.getPublishedVersionId() != null) {
        input.put("workflowVersionId", published.getPublishedVersionId());
      }
    }
    step(job, context, "graph_execute", 15, "执行热点成文工作流");
    Map<String, Object> state = new LinkedHashMap<>(input);
    state.put("articleId", articleId);
    state.put("jobId", job.getId());
    workflows.attachJobRun(workflowId, job.getId());
    WorkflowGraphExecuteResult result = workflows.executeGraphForJob(workflowId, job.getId(), state);
    context.renewLease(job.getId());
    // 图失败时保留节点进度步，避免失败事件挂到 graph_finalize
    if (WorkflowRunStatus.FAILED.getValue().equals(result.getStatus())) {
      throw new IllegalStateException(
          result.getErrorMessage() == null ? "热点成文工作流执行失败" : result.getErrorMessage());
    }
    step(job, context, "graph_finalize", 90, "汇总工作流结果");
    HotNewsArticleGenerationJobOutput output =
        HotNewsArticleGenerationJobOutput.fromGraph(result.toJobOutput());
    output.put("articleId", articleId);
    output.putIfAbsent("partial", false);
    // 透出 workflowRunId，便于执行轨迹页加载节点明细
    Object runId = output.get("runId");
    if (runId != null && StrUtil.isNotBlank(String.valueOf(runId))) {
      output.putIfAbsent("workflowRunId", String.valueOf(runId));
    }
    context.completeWith(output);
    step(job, context, "done", 100, "热点文章生成完成");
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
