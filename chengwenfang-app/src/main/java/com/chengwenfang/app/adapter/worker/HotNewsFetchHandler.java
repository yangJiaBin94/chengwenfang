package com.chengwenfang.app.adapter.worker;

import com.chengwenfang.mode.hotnews.application.generation.RadarCollectPipeline;
import com.chengwenfang.job.domain.entity.JobEntity;
import com.chengwenfang.job.facade.JobExecutionContext;
import com.chengwenfang.job.facade.JobHandler;
import com.chengwenfang.job.facade.constant.JobTypes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 热点雷达采集任务：委托 FormalGraph 管道（同步五阶段 + 节点进度）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotNewsFetchHandler implements JobHandler {
  /** 雷达采集管道。 */
  private final RadarCollectPipeline pipeline;

  @Override
  public String type() {
    return JobTypes.HOT_NEWS_FETCH;
  }

  /**
   * 执行雷达采集图。
   *
   * @param job     当前 Job
   * @param context 执行上下文
   * @throws Exception 管道异常
   */
  @Override
  public void execute(JobEntity job, JobExecutionContext context) throws Exception {
    log.info("热点雷达采集 start jobId={}", job.getId());
    pipeline.run(job, context);
  }
}
