package com.chengwenfang.app.adapter.worker;

import com.chengwenfang.mode.course.application.generation.WechatArticlePipeline;
import com.chengwenfang.job.domain.entity.JobEntity;
import com.chengwenfang.job.facade.JobExecutionContext;
import com.chengwenfang.job.facade.JobHandler;
import com.chengwenfang.job.facade.constant.JobTypes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 公众号文章生成任务处理器。
 * <p>委托 {@link WechatArticlePipeline}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WechatArticleGenerationHandler implements JobHandler {
  /** 公众号文章转换管道。 */
  private final WechatArticlePipeline pipeline;

  /**
   * @return {@link JobTypes#WECHAT_ARTICLE_GENERATION}
   */
  @Override
  public String type() {
    return JobTypes.WECHAT_ARTICLE_GENERATION;
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
    log.info("公众号文章生成 start jobId={}", job.getId());
    // 委托公众号文章生成管道
    pipeline.run(job, context);
  }
}
