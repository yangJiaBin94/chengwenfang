package com.workbench.app.adapter.worker;

import com.workbench.mode.course.application.course.ThemeCurriculumOrchestrator;
import com.workbench.job.domain.entity.JobEntity;
import com.workbench.job.facade.JobExecutionContext;
import com.workbench.job.facade.JobHandler;
import com.workbench.job.facade.constant.JobTypes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 主题课表（系列/课次）生成任务处理器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ThemeCurriculumGenerationHandler implements JobHandler {
  private final ThemeCurriculumOrchestrator orchestrator;

  @Override
  public String type() {
    return JobTypes.THEME_CURRICULUM_GENERATION;
  }

  /**
   * @param job     entityId=themeId
   * @param context 执行上下文
   */
  @Override
  public void execute(JobEntity job, JobExecutionContext context) {
    log.info("主题课表生成 start jobId={} themeId={}", job.getId(), job.getEntityId());
    orchestrator.generate(job, context);
  }
}
