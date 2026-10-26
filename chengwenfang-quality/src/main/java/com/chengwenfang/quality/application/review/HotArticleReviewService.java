package com.chengwenfang.quality.application.review;

import com.chengwenfang.mode.hotnews.facade.dto.request.HotNewsArticleUpdateRequest;
import com.chengwenfang.mode.hotnews.facade.HotNewsFacade;
import com.chengwenfang.job.facade.constant.JobEntityTypes;
import com.chengwenfang.job.facade.JobFacade;
import com.chengwenfang.job.facade.constant.JobTypes;
import com.chengwenfang.job.facade.dto.job.HotArticleReviewJobInput;
import com.chengwenfang.job.facade.dto.job.HotArticleReviewRepairJobInput;
import com.chengwenfang.job.facade.dto.job.HotArticleReviewRevalidateJobInput;
import com.chengwenfang.job.facade.dto.view.JobView;
import com.chengwenfang.quality.facade.dto.view.HotArticleAuditView;
import com.chengwenfang.quality.facade.dto.view.HotArticleRepairCandidateView;
import com.chengwenfang.quality.facade.dto.view.HotArticleRepairRejectView;
import com.chengwenfang.quality.facade.dto.request.HotArticleReviewCreateRequest;
import com.chengwenfang.quality.facade.dto.view.HotArticleReviewDecisionView;
import com.chengwenfang.quality.facade.dto.view.HotArticleReviewEngineResult;
import com.chengwenfang.quality.facade.HotArticleReviewFacade;
import com.chengwenfang.quality.facade.constant.ArticleAuditDomains;
import com.chengwenfang.quality.facade.dto.request.HotArticleReviewRunCreateRequest;
import com.chengwenfang.quality.facade.dto.view.HotArticleReviewRunView;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 热点文章审核编排：Job 创建与热点回写，持久化委托 {@link HotArticleReviewFacade}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotArticleReviewService {
  /** 热点审核门面：运行、候选、裁定。 */
  private final HotArticleReviewFacade reviews;
  /** 热点资讯门面：读文章、写回修复稿。 */
  private final HotNewsFacade hotNews;
  /** Job 门面：创建审核/修复/复检任务。 */
  private final JobFacade jobs;

  /**
   * 创建审核任务：补齐正文/来源后入队 {@link JobTypes#HOT_ARTICLE_REVIEW}，并创建 pending 运行。
   *
   * @param body 创建请求；须尽量含 articleId，可缺 content/sources
   * @return 新建的 Job 视图
   */
  @Transactional
  public JobView createReview(HotArticleReviewCreateRequest body) {
    HotArticleReviewJobInput input = new HotArticleReviewJobInput();
    if (body != null) {
      if (body.getArticleId() != null) {
        input.put("articleId", body.getArticleId());
      }
      if (body.getDocumentId() != null && !body.getDocumentId().isBlank()) {
        input.put("documentId", body.getDocumentId());
      }
      if (body.getMode() != null) {
        input.put("mode", body.getMode());
      }
      if (body.getContentHash() != null) {
        input.put("contentHash", body.getContentHash());
      }
      if (body.getStandardVersion() != null) {
        input.put("standardVersion", body.getStandardVersion());
      }
      if (body.getContent() != null) {
        input.put("content", body.getContent());
      }
      if (body.getSources() != null) {
        input.put("sources", body.getSources());
      }
    }
    String articleId = String.valueOf(input.getOrDefault("articleId", ""));
    if (articleId.isBlank() || "null".equals(articleId)) {
      articleId = ArticleAuditDomains.ARTICLE_UNKNOWN;
    }
    log.info("创建热点文章审核 articleId={}", articleId);
    // 从热点文章补齐 content/sources/contentHash，避免空跑审核
    enrichReviewInput(articleId, input);
    JobView job = jobs.create(
        JobTypes.HOT_ARTICLE_REVIEW,
        JobEntityTypes.ARTICLE,
        articleId,
        input);

    reviews.createPendingRun(
        job.getId(), articleId, job.getId(), HotArticleReviewRunCreateRequest.fromMap(input));
    return job;
  }

  /**
   * 持久化审核引擎结果（条目与运行状态）。
   *
   * @param reviewId 审核运行 ID（通常与 jobId 对齐）
   * @param output   引擎输出
   */
  public void persistReviewResult(String reviewId, HotArticleReviewEngineResult output) {
    reviews.persistReviewResult(reviewId, output);
  }

  /**
   * 查询文章最新一次热点审核运行。
   *
   * @param articleId 文章 ID
   * @return 最新审核视图
   */
  public HotArticleAuditView latest(String articleId) {
    return reviews.latest(articleId);
  }

  /**
   * 查询文章最新一次热点审核运行。
   *
   * @param articleId  文章 ID
   * @param documentId 统一文档 id；可空
   * @return 最新审核视图
   */
  public HotArticleAuditView latest(String articleId, String documentId) {
    return reviews.latest(articleId, documentId);
  }

  /**
   * 对审核失败项发起修复 Job。
   *
   * @param reviewId  审核运行 ID
   * @param issueIds  指定修复的问题 ID 列表；repairAll 时忽略
   * @param repairAll true 时修复全部失败项；否则使用 issueIds
   * @return 新建的修复 Job
   */
  @Transactional
  public JobView startRepair(String reviewId, Object issueIds, boolean repairAll) {
    HotArticleReviewRunView run = reviews.requireRun(reviewId);
    HotArticleReviewRepairJobInput input = new HotArticleReviewRepairJobInput();
    input.put("reviewId", reviewId);
    input.put("articleId", run.getArticleId());
    input.put("contentHash", run.getContentHash() == null ? "" : run.getContentHash());
    input.put("standardVersion",
        run.getStandardVersion() == null
            ? ArticleAuditDomains.STANDARD_HOT_NEWS_V1
            : run.getStandardVersion());
    enrichReviewInput(run.getArticleId(), input);
    if (repairAll) {
      input.put("repairAll", true);
    } else {
      input.put("issueIds", issueIds == null ? List.of() : issueIds);
    }
    return jobs.create(
        JobTypes.HOT_ARTICLE_REVIEW_REPAIR,
        JobEntityTypes.ARTICLE,
        run.getArticleId(),
        input);
  }

  /**
   * 保存修复候选稿。
   *
   * @param reviewId    审核运行 ID
   * @param articleId   文章 ID
   * @param jobId       修复 Job ID
   * @param content     候选正文
   * @param issueIds    关联问题 ID
   * @param diffSummary 差异摘要
   * @return 候选视图
   */
  public HotArticleRepairCandidateView saveRepairCandidate(
      String reviewId,
      String articleId,
      String jobId,
      String content,
      Object issueIds,
      String diffSummary) {
    return reviews.saveRepairCandidate(reviewId, articleId, jobId, content, issueIds, diffSummary);
  }

  /**
   * 接受修复候选：写回热点正文、标记候选已接受，并触发复检 Job。
   *
   * @param candidateId 候选 ID
   * @return 复检 Job 视图
   */
  @Transactional
  public JobView acceptRepair(String candidateId) {
    HotArticleRepairCandidateView candidate = reviews.getCandidate(candidateId);
    String articleId = candidate.getArticleId();
    // 先写回热点正文，再标记候选，保证复检读到新稿
    hotNews.updateHotArticle(
        articleId,
        HotNewsArticleUpdateRequest.builder()
            .content(candidate.getContent() == null ? "" : candidate.getContent())
            .build());
    reviews.markCandidateAccepted(candidateId);

    HotArticleReviewRevalidateJobInput input = new HotArticleReviewRevalidateJobInput();
    input.put("candidateId", candidateId);
    input.put("action", "accept");
    input.put("articleId", articleId);
    input.put("reviewId", candidate.getReviewRunId());
    enrichReviewInput(articleId, input);
    return jobs.create(
        JobTypes.HOT_ARTICLE_REVIEW_REVALIDATE,
        JobEntityTypes.ARTICLE,
        articleId,
        input);
  }

  /**
   * 读取修复候选详情。
   *
   * @param candidateId 候选 ID
   * @return 候选视图
   */
  public HotArticleRepairCandidateView getCandidate(String candidateId) {
    return reviews.getCandidate(candidateId);
  }

  /**
   * 拒绝修复候选。
   *
   * @param candidateId 候选 ID
   * @return 拒绝结果视图
   */
  public HotArticleRepairRejectView rejectRepair(String candidateId) {
    return reviews.rejectRepair(candidateId);
  }

  /**
   * 人工裁定审核结果（通过/驳回等）。
   *
   * @param reviewId 审核运行 ID
   * @param status   裁定状态
   * @param comment  备注；可空
   * @return 裁定结果视图
   */
  public HotArticleReviewDecisionView decide(String reviewId, String status, String comment) {
    return reviews.decide(reviewId, status, comment);
  }

  /**
   * 从热点文章补齐审核输入中的正文、来源与 contentHash。
   *
   * @param articleId 文章 ID
   * @param input     可变输入（就地补齐）
   */
  private void enrichReviewInput(String articleId, java.util.Map<String, Object> input) {
    boolean missingContent = !input.containsKey("content")
        || String.valueOf(input.get("content")).isBlank();
    boolean missingSources = !(input.get("sources") instanceof List<?>);
    if (!missingContent && !missingSources) {
      input.putIfAbsent("contentHash", "");
      input.putIfAbsent("standardVersion", ArticleAuditDomains.STANDARD_HOT_NEWS_V1);
      return;
    }
    try {
      var article = hotNews.getHotArticle(articleId);
      if (missingContent) {
        // 优先正式 content，空则退回 draftContent
        Object content = article.getContent();
        if (content == null || String.valueOf(content).isBlank()) {
          content = article.getDraftContent();
        }
        input.put("content", content == null ? "" : String.valueOf(content));
      }
      if (!input.containsKey("contentHash") || String.valueOf(input.get("contentHash")).isBlank()) {
        input.put("contentHash", article.getContentHash() == null ? "" : article.getContentHash());
      }
      if (missingSources) {
        Object sources = article.getSourceSnapshot();
        input.put("sources", sources instanceof List<?> ? sources : List.of());
      }
    } catch (Exception ignored) {
      // 文章不存在时仍给空字段，避免创建 Job 直接失败
      input.putIfAbsent("content", "");
      input.putIfAbsent("sources", List.of());
    }
    input.putIfAbsent("contentHash", "");
    input.putIfAbsent("standardVersion", ArticleAuditDomains.STANDARD_HOT_NEWS_V1);
  }
}
