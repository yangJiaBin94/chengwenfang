package com.workbench.app.adapter.worker;

import com.workbench.content.application.generation.ImageGenerationPipeline;
import com.workbench.job.domain.entity.JobEntity;
import com.workbench.job.facade.JobExecutionContext;
import com.workbench.job.facade.JobHandler;
import com.workbench.job.facade.constant.JobTypes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 文章配图生成任务处理器。
 * <p>委托 {@link ImageGenerationPipeline}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageGenerationHandler implements JobHandler {
  /** 配图生成管道。 */
  private final ImageGenerationPipeline pipeline;

  /**
   * @return {@link JobTypes#IMAGE_GENERATION}
   */
  @Override
  public String type() {
    return JobTypes.IMAGE_GENERATION;
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
    log.info("文章配图生成 start jobId={}", job.getId());
    pipeline.run(job, context);
  }
}
