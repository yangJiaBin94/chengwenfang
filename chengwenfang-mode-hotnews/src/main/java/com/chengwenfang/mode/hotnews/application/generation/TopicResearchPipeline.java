package com.chengwenfang.mode.hotnews.application.generation;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.job.domain.entity.JobEntity;
import com.chengwenfang.job.facade.JobExecutionContext;
import com.chengwenfang.job.facade.dto.job.HotNewsTopicResearchJobInput;
import com.chengwenfang.job.facade.dto.job.HotNewsTopicResearchJobOutput;
import com.chengwenfang.job.facade.dto.job.JobPayloads;
import com.chengwenfang.mode.hotnews.facade.HotNewsFacade;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsResearchCompleteView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 热点选题调研管道。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TopicResearchPipeline {
  /** 热点资讯门面。 */
  private final HotNewsFacade hotNews;

  /**
   * 执行热点选题调研管道。
   *
   * @param job     任务实体
   * @param context 执行上下文
   * @throws Exception 调研完成失败
   */
  public void run(JobEntity job, JobExecutionContext context) throws Exception {
    log.info("执行管道 hotNewsTopicResearch jobId={} entityId={}", job.getId(), job.getEntityId());
    HotNewsTopicResearchJobInput input =
        JobPayloads.parse(job.getInputJson(), HotNewsTopicResearchJobInput.class);
    String researchRunId = StrUtil.firstNonBlank(stringOrNull(input.getResearchRunId()), job.getEntityId());
    String topicId = stringOrNull(input.getTopicId());

    step(job, context, "research_search", 20, "检索选题来源");
    context.renewLease(job.getId());
    step(job, context, "research_extract", 45, "提取证据");
    context.renewLease(job.getId());
    step(job, context, "research_analyze", 70, "分析调研条目");
    HotNewsResearchCompleteView result = hotNews.completeTopicResearch(researchRunId, topicId);
    context.renewLease(job.getId());
    step(job, context, "research_finished", 100, "选题调研完成");
    context.completeWith(HotNewsTopicResearchJobOutput.builder()
        .researchRunId(result.getResearchRunId())
        .topicId(result.getTopicId())
        .itemCount(result.getItemCount())
        .conflictCount(result.getConflictCount())
        .status(result.getStatus())
        .briefId(result.getBriefId())
        .build());
  }

  /**
   * 上报一步进度（已取消则跳过）；顺带续租。
   *
   * @param job      任务
   * @param context  上下文
   * @param name     步骤名
   * @param progress 进度 0–100
   * @param message  人类可读说明
   */
  private void step(JobEntity job, JobExecutionContext context, String name, int progress, String message) {
    if (context.isCancelled(job.getId())) {
      return;
    }
    context.renewLease(job.getId());
    context.progress(job.getId(), name, progress, message);
  }

  /**
   * 将任意值转为非空字符串；blank/null 字符串返回 null。
   *
   * @param value 任意对象
   * @return 字符串或 null
   */
  private static String stringOrNull(Object value) {
    if (value == null) {
      return null;
    }
    String text = String.valueOf(value);
    return text.isBlank() || "null".equals(text) ? null : text;
  }
}
