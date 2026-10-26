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
 * 雷达信源视频自动解析任务：解析失败弃用，成功则事实速览并营销判定。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotNewsSourceVideoParseHandler implements JobHandler {
  /** 热点门面。 */
  private final HotNewsFacade hotNews;

  @Override
  public String type() {
    return JobTypes.HOT_NEWS_SOURCE_VIDEO_PARSE;
  }

  @Override
  public void execute(JobEntity job, JobExecutionContext context) {
    log.info("雷达信源视频解析 start jobId={}", job.getId());
    hotNews.executeSourceVideoParseJob(job.getId(), job.getInputJson(), context);
  }
}
