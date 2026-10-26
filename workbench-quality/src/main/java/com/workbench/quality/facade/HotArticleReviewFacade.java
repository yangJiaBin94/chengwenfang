package com.workbench.quality.facade;


import com.workbench.quality.facade.dto.request.HotArticleReviewRunCreateRequest;
import com.workbench.quality.facade.dto.view.HotArticleAuditView;
import com.workbench.quality.facade.dto.view.HotArticleRepairCandidateView;
import com.workbench.quality.facade.dto.view.HotArticleRepairRejectView;
import com.workbench.quality.facade.dto.view.HotArticleReviewDecisionView;
import com.workbench.quality.facade.dto.view.HotArticleReviewEngineResult;
import com.workbench.quality.facade.dto.view.HotArticleReviewRunView;
/**
 * 热点文章审稿门面：审稿运行/条目/修复候选的持久化与查询。
 * Job 创建与热点正文回写由 app 编排层负责。
 */
public interface HotArticleReviewFacade {
  /** 创建 pending 审稿运行行（id 通常等于 jobId）。 */
  HotArticleReviewRunView createPendingRun(
      String reviewId, String articleId, String jobId, HotArticleReviewRunCreateRequest input);

  /** 持久化审核引擎结果与条目。 */
  void persistReviewResult(String reviewId, HotArticleReviewEngineResult output);

  /** 文章最近一次审稿视图。 */
  HotArticleAuditView latest(String articleId);

  /**
   * 文章最近一次审稿视图。
   *
   * @param articleId  热点文章 ID
   * @param documentId 统一文档 id；可空
   */
  HotArticleAuditView latest(String articleId, String documentId);

  /** 加载运行；不存在抛业务异常。 */
  HotArticleReviewRunView requireRun(String reviewId);

  /** 保存修复候选，返回候选视图。 */
  HotArticleRepairCandidateView saveRepairCandidate(
      String reviewId,
      String articleId,
      String jobId,
      String content,
      Object issueIds,
      String diffSummary);

  /** 标记候选为 accepted。 */
  HotArticleRepairCandidateView markCandidateAccepted(String candidateId);

  /** 标记候选为 rejected。 */
  HotArticleRepairRejectView rejectRepair(String candidateId);

  /** 写入人工裁决字段。 */
  HotArticleReviewDecisionView decide(String reviewId, String status, String comment);

  /** 获取候选详情。 */
  HotArticleRepairCandidateView getCandidate(String candidateId);
}
