package com.chengwenfang.app.adapter.worker;

import com.chengwenfang.quality.application.generation.ArticleRepairPipeline;
import com.chengwenfang.job.domain.entity.JobEntity;
import com.chengwenfang.job.facade.JobExecutionContext;
import com.chengwenfang.job.facade.JobHandler;
import com.chengwenfang.job.facade.constant.JobTypes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 文章 AI 修复任务处理器。
 * <p>执行前可选调用 {@link JobExecutionHook}（测试闸门）；再委托 {@link ArticleRepairPipeline}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleRepairHandler implements JobHandler {
  /** 文章修复管道。 */
  private final ArticleRepairPipeline pipeline;
  /** 可选执行钩子（测试 profile 下为 JobTestGate）。 */
  private final ObjectProvider<JobExecutionHook> hooks;

  /**
   * 返回本 Handler 处理的 Job 类型。
   *
   * @return {@link JobTypes#ARTICLE_REPAIR}
   */
  @Override
  public String type() {
    return JobTypes.ARTICLE_REPAIR;
  }

  /**
   * 执行修复；任一 hook 返回 true 则短路（例如测试中已取消）。
   *
   * @param job     当前 Job
   * @param context 执行上下文（进度/终态）
   * @throws Exception 管道或 hook 异常
   */
  @Override
  public void execute(JobEntity job, JobExecutionContext context) throws Exception {
    log.info("文章修复开始 jobId={}", job.getId());
    for (JobExecutionHook hook : hooks) {
      if (hook.beforeArticleRepair(job, context)) {
        log.info("文章修复被 hook 短路 jobId={}", job.getId());
        return;
      }
    }
    pipeline.run(job, context);
  }
}
