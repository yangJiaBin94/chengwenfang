package com.workbench.app.adapter.worker;

import com.workbench.mode.course.application.generation.CourseDraftPipeline;
import com.workbench.job.domain.entity.JobEntity;
import com.workbench.job.facade.JobExecutionContext;
import com.workbench.job.facade.JobHandler;
import com.workbench.job.facade.constant.JobTypes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 课程草稿生成任务处理器。
 * <p>委托 {@link CourseDraftPipeline}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CourseDraftGenerationHandler implements JobHandler {
  /** 课程草稿生成管道。 */
  private final CourseDraftPipeline pipeline;

  /**
   * @return {@link JobTypes#COURSE_DRAFT_GENERATION}
   */
  @Override
  public String type() {
    return JobTypes.COURSE_DRAFT_GENERATION;
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
    log.info("课程草稿生成 start jobId={}", job.getId());
    // 委托课程草稿生成管道
    pipeline.run(job, context);
  }
}
