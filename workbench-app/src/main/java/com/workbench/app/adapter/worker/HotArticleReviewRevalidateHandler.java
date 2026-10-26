package com.workbench.app.adapter.worker;

import com.workbench.quality.application.generation.HotArticleRevalidatePipeline;
import com.workbench.job.domain.entity.JobEntity;
import com.workbench.job.facade.JobExecutionContext;
import com.workbench.job.facade.JobHandler;
import com.workbench.job.facade.constant.JobTypes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 热点文章复检任务处理器。
 * <p>委托 {@link HotArticleRevalidatePipeline}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotArticleReviewRevalidateHandler implements JobHandler {
  /** 热点审稿复检管道。 */
  private final HotArticleRevalidatePipeline pipeline;

  /**
   * @return {@link JobTypes#HOT_ARTICLE_REVIEW_REVALIDATE}
   */
  @Override
  public String type() {
    return JobTypes.HOT_ARTICLE_REVIEW_REVALIDATE;
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
    log.info("热点文章复检 start jobId={}", job.getId());
    // 委托热点审稿复检管道
    pipeline.run(job, context);
  }
}
