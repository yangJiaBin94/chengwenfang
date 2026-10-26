package com.workbench.app.adapter.worker;

import com.workbench.mode.hotnews.facade.HotNewsFacade;
import com.workbench.job.domain.entity.JobEntity;
import com.workbench.job.facade.JobExecutionContext;
import com.workbench.job.facade.JobHandler;
import com.workbench.job.facade.constant.JobTypes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 热点资料池视频解析任务处理器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotNewsVideoParseHandler implements JobHandler {
  /** 热点门面。 */
  private final HotNewsFacade hotNews;

  @Override
  public String type() {
    return JobTypes.HOT_NEWS_VIDEO_PARSE;
  }

  @Override
  public void execute(JobEntity job, JobExecutionContext context) {
    log.info("热点视频解析 start jobId={}", job.getId());
    hotNews.executeVideoParseJob(job.getId(), job.getInputJson(), context);
  }
}
