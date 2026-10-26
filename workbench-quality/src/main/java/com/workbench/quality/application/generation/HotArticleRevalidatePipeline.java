package com.workbench.quality.application.generation;

import cn.hutool.core.bean.BeanUtil;
import com.workbench.job.domain.entity.JobEntity;
import com.workbench.job.facade.JobExecutionContext;
import com.workbench.job.facade.dto.job.HotArticleReviewJobOutput;
import com.workbench.job.facade.dto.job.HotArticleReviewRevalidateJobInput;
import com.workbench.job.facade.dto.job.JobPayloads;
import com.workbench.quality.facade.AuditFacade;
import com.workbench.quality.facade.constant.ArticleAuditDomains;
import com.workbench.quality.facade.dto.request.HotArticleReviewSnapshotRequest;
import com.workbench.quality.facade.dto.view.HotArticleRuleResultView;
import com.workbench.quality.facade.enums.ReviewStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 热点文章复检管道。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotArticleRevalidatePipeline {
  /** 规则审计门面。 */
  private final AuditFacade audit;

  /**
   * 执行热点文章复检管道。
   *
   * @param job     任务实体
   * @param context 执行上下文
   * @throws Exception 复检失败
   */
  public void run(JobEntity job, JobExecutionContext context) throws Exception {
    log.info("执行管道 hotArticleReviewRevalidate jobId={} entityId={}", job.getId(), job.getEntityId());
    HotArticleReviewRevalidateJobInput input = HotArticleReviewRevalidateJobInput.from(
        JobPayloads.parseMap(job.getInputJson()));
    step(job, context, "revalidate", 50, "复检文章审核");
    context.completeWith(runReview(input));
    step(job, context, "revalidate", 100, "复检完成");
  }

  /**
   * 按入参跑热点规则审稿，组装 status/results 输出。
   *
   * @param input 含 content/contentHash/sources/standardVersion
   * @return Job 出参
   */
  @SuppressWarnings("unchecked")
  private HotArticleReviewJobOutput runReview(Map<String, Object> input) {
    List<Map<String, Object>> sources = (List<Map<String, Object>>) input.getOrDefault("sources", List.of());
    HotArticleReviewSnapshotRequest snapshot = HotArticleReviewSnapshotRequest.builder()
        .content(String.valueOf(input.getOrDefault("content", "")))
        .contentHash(String.valueOf(input.getOrDefault("contentHash", "")))
        .sources(sources == null ? List.of() : sources)
        .standardVersion(String.valueOf(input.getOrDefault(
            "standardVersion", ArticleAuditDomains.STANDARD_HOT_NEWS_V1)))
        .build();
    List<HotArticleRuleResultView> results = audit.runHotArticleRuleReview(snapshot);
    boolean blockingFailed = results.stream()
        .anyMatch(item -> ReviewStatus.from(item.getStatus()).isFailLike()
            && Boolean.TRUE.equals(item.getBlocking()));
    return HotArticleReviewJobOutput.of(
        blockingFailed ? ReviewStatus.FAILED.getValue() : ReviewStatus.PASSED.getValue(),
        blockingFailed,
        results.stream()
            .map(row -> BeanUtil.beanToMap(row, new LinkedHashMap<>(), false, true))
            .toList());
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
}
