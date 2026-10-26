package com.chengwenfang.app.adapter.worker;

import com.chengwenfang.quality.application.generation.HotArticleReviewRepairPipeline;
import com.chengwenfang.job.domain.entity.JobEntity;
import com.chengwenfang.job.facade.JobExecutionContext;
import com.chengwenfang.job.facade.JobHandler;
import com.chengwenfang.job.facade.constant.JobTypes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 热点文章审核修复任务处理器。
 * <p>委托 {@link HotArticleReviewRepairPipeline}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotArticleReviewRepairHandler implements JobHandler {
  /** 热点审稿修复管道。 */
  private final HotArticleReviewRepairPipeline pipeline;

  /**
   * @return {@link JobTypes#HOT_ARTICLE_REVIEW_REPAIR}
   */
  @Override
  public String type() {
    return JobTypes.HOT_ARTICLE_REVIEW_REPAIR;
  }

  /**
   * 执行任务；进度与终态通过 context 回写。
   *
   * @param job     当前 Job
   * @param context 执行上下文
   * @throws Exception 管道异常
   */
  @Override
  public void execute(JobEntity job, JobExecutionContext context) throws Exception {
    log.info("热点文章审核修复 start jobId={}", job.getId());
    // 委托热点审核修复管道
    pipeline.run(job, context);
  }
}
