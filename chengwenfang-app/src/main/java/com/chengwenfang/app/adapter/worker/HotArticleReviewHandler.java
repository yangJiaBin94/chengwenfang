package com.chengwenfang.app.adapter.worker;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.chengwenfang.job.domain.entity.JobEntity;
import com.chengwenfang.job.facade.JobExecutionContext;
import com.chengwenfang.job.facade.JobHandler;
import com.chengwenfang.job.facade.constant.JobTypes;
import com.chengwenfang.job.facade.dto.job.HotArticleReviewJobInput;
import com.chengwenfang.job.facade.dto.job.HotArticleReviewJobOutput;
import com.chengwenfang.job.facade.dto.job.JobPayloads;
import com.chengwenfang.quality.facade.AuditFacade;
import com.chengwenfang.quality.facade.HotArticleReviewFacade;
import com.chengwenfang.quality.facade.constant.ArticleAuditDomains;
import com.chengwenfang.quality.facade.dto.request.HotArticleReviewSnapshotRequest;
import com.chengwenfang.quality.facade.dto.view.HotArticleReviewEngineResult;
import com.chengwenfang.quality.facade.dto.view.HotArticleRuleResultView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 热点文章审核任务处理器：跑规则引擎并持久化结果。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotArticleReviewHandler implements JobHandler {
  /** 审核门面：热点规则引擎。 */
  private final AuditFacade audit;
  /** 热点审核门面：落库运行结果。 */
  private final HotArticleReviewFacade hotArticleReviews;

  /**
   * @return {@link JobTypes#HOT_ARTICLE_REVIEW}
   */
  @Override
  public String type() {
    return JobTypes.HOT_ARTICLE_REVIEW;
  }

  /**
   * 执行热点文章规则审核：构造内容快照、调用审计引擎并持久化结果。
   *
   * @param job     当前 Job（inputJson 含 content/sources 等）
   * @param context 执行上下文
   * @throws Exception JSON 解析或持久化失败
   */
  @Override
  public void execute(JobEntity job, JobExecutionContext context) throws Exception {
    log.info("热点文章审核开始 jobId={}", job.getId());
    context.progress(job.getId(), "review", 20, "Running rule review");
    HotArticleReviewJobInput input = HotArticleReviewJobInput.from(JobPayloads.parseMap(job.getInputJson()));
    List<Map<String, Object>> sources = input.sources();
    HotArticleReviewSnapshotRequest snapshot = HotArticleReviewSnapshotRequest.builder()
        .content(ObjectUtil.defaultIfNull(input.content(), ""))
        .contentHash(ObjectUtil.defaultIfNull(input.contentHash(), ""))
        .sources(ObjectUtil.defaultIfNull(sources, List.of()))
        .standardVersion(ArticleAuditDomains.STANDARD_HOT_NEWS_V1)
        .build();
    List<HotArticleRuleResultView> results = audit.runHotArticleRuleReview(snapshot);
    // 任一阻塞规则失败则整次审核 failed
    boolean blockingFailed = results.stream()
        .anyMatch(item -> "failed".equals(item.getStatus()) && Boolean.TRUE.equals(item.getBlocking()));
    context.progress(job.getId(), "review", 90, "Rule review finished");
    context.event(job.getId(), "review_done", "review", 95, "Rule review completed",
        Map.of("blockingFailed", blockingFailed, "issueCount", results.size()));
    HotArticleReviewJobOutput output = HotArticleReviewJobOutput.of(
        blockingFailed ? "failed" : "passed",
        blockingFailed,
        results.stream().map(this::toResultMap).toList());
    // reviewId 与 jobId 对齐，供后续修复/裁定定位
    hotArticleReviews.persistReviewResult(job.getId(), HotArticleReviewEngineResult.fromMap(output));
    context.completeWith(output);
    log.info("热点文章审核完成 jobId={} blockingFailed={} issues={}",
        job.getId(), blockingFailed, results.size());
  }

  /**
   * 规则结果视图转为可落库 Map（合并 extras）。
   *
   * @param row 规则结果
   * @return 扁平字段 Map
   */
  private Map<String, Object> toResultMap(HotArticleRuleResultView row) {
    Map<String, Object> mapped = BeanUtil.beanToMap(row, new LinkedHashMap<>(), false, true);
    mapped.remove("extras");
    if (row.getExtras() != null) {
      mapped.putAll(row.getExtras());
    }
    return mapped;
  }
}
