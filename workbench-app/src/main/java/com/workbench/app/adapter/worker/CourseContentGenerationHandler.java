package com.workbench.app.adapter.worker;

import com.workbench.mode.course.application.generation.CourseContentPipeline;
import com.workbench.job.domain.entity.JobEntity;
import com.workbench.job.facade.JobExecutionContext;
import com.workbench.job.facade.JobHandler;
import com.workbench.job.facade.constant.JobTypes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 正式课程内容生成任务处理器。
 * <p>委托 {@link CourseContentPipeline}（正式编排或图执行）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CourseContentGenerationHandler implements JobHandler {
  /** 课程内容生成管道。 */
  private final CourseContentPipeline pipeline;

  /**
   * @return {@link JobTypes#COURSE_CONTENT_GENERATION}
   */
  @Override
  public String type() {
    return JobTypes.COURSE_CONTENT_GENERATION;
  }

  /**
   * 执行任务；进度与终态通过 context 回写。
   *
   * @param job     当前 Job（entityId=courseId）
   * @param context 执行上下文
   * @throws Exception 管道异常
   */
  @Override
  public void execute(JobEntity job, JobExecutionContext context) throws Exception {
    log.info("正式课程内容生成 start jobId={}", job.getId());
    // 委托正式课程内容生成管道
    pipeline.run(job, context);
  }
}
