package com.chengwenfang.app.adapter.web.hotnews;

import com.chengwenfang.quality.application.review.HotArticleReviewService;
import com.chengwenfang.common.ApiResult;
import com.chengwenfang.content.facade.enums.HumanReviewStatus;
import com.chengwenfang.job.facade.dto.view.JobView;
import com.chengwenfang.quality.facade.dto.view.HotArticleAuditView;
import com.chengwenfang.quality.facade.dto.view.HotArticleRepairCandidateView;
import com.chengwenfang.quality.facade.dto.view.HotArticleRepairRejectView;
import com.chengwenfang.quality.facade.dto.request.HotArticleReviewCreateRequest;
import com.chengwenfang.quality.facade.dto.request.HotArticleReviewDecisionRequest;
import com.chengwenfang.quality.facade.dto.view.HotArticleReviewDecisionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 热点文章审核与修复相关 HTTP 接口。
 * <p>编排入口在 {@link HotArticleReviewService}，本类负责请求体拆装与默认值。
 */
@Tag(name = "热点审核", description = "热点文章审核运行、修复候选与裁定")
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HotArticleReviewController {
  /** 热点审稿应用服务（创建任务、修复、裁决）。 */
  private final HotArticleReviewService hotArticleReviews;

  /**
   * 启动热点文章审核任务。
   *
   * @param body 可选创建请求；非空字段写入任务 input
   * @return 已入队的审核 Job 视图
   */
  @PostMapping("/hot-article-reviews")
  @Operation(summary = "启动热点文章审核")
  public ApiResult<JobView> createHotArticleReview(
      @Valid @RequestBody(required = false) HotArticleReviewCreateRequest body) {
    return ApiResult.ok(hotArticleReviews.createReview(body));
  }

  /**
   * 获取文章最近一次热点审核汇总。
   *
   * @param articleId  热点文章 ID
   * @param documentId 统一文档 id；可空
   * @return 审稿汇总视图；无记录时由服务返回 none 占位
   */
  @GetMapping("/articles/{articleId}/hot-reviews/latest")
  @Operation(summary = "获取最新热点审核运行")
  public ApiResult<HotArticleAuditView> latestHotArticleReview(
      @PathVariable String articleId,
      @RequestParam(required = false) String documentId) {
    return ApiResult.ok(hotArticleReviews.latest(articleId, documentId));
  }

  /**
   * 对审核失败项发起修复（可指定条目）。
   *
   * @param reviewId 审稿运行 ID
   * @param body     可选修复范围/条目载荷，透传给服务
   * @return 修复任务 Job 视图
   */
  @PostMapping("/hot-article-reviews/{reviewId}/repair")
  @Operation(summary = "对审核失败项发起修复")
  public ApiResult<JobView> repairHotArticleReview(
      @PathVariable String reviewId, @Valid @RequestBody(required = false) Object body) {
    // repairAll=false：按 body 指定范围修复
    return ApiResult.ok(hotArticleReviews.startRepair(reviewId, body, false));
  }

  /**
   * 批量修复该次审核的全部失败项。
   *
   * @param reviewId 审稿运行 ID
   * @return 修复任务 Job 视图
   */
  @PostMapping("/hot-article-reviews/{reviewId}/repair-all")
  @Operation(summary = "批量修复审核失败项")
  public ApiResult<JobView> repairAllHotArticleReview(@PathVariable String reviewId) {
    // 空列表 + repairAll=true：全量修复
    return ApiResult.ok(hotArticleReviews.startRepair(reviewId, List.of(), true));
  }

  /**
   * 获取热点修复候选详情。
   *
   * @param candidateId 修复候选 ID
   * @return 候选视图（含 issueIdsJson）
   */
  @GetMapping("/hot-article-repairs/{candidateId}")
  @Operation(summary = "获取热点修复候选")
  public ApiResult<HotArticleRepairCandidateView> getHotArticleRepair(
      @PathVariable String candidateId) {
    return ApiResult.ok(hotArticleReviews.getCandidate(candidateId));
  }

  /**
   * 接受热点修复候选并入队落稿。
   *
   * @param candidateId 修复候选 ID
   * @return 接受后触发的 Job 视图
   */
  @PostMapping("/hot-article-repairs/{candidateId}/accept")
  @Operation(summary = "接受热点修复候选")
  public ApiResult<JobView> acceptHotArticleRepair(@PathVariable String candidateId) {
    return ApiResult.ok(hotArticleReviews.acceptRepair(candidateId));
  }

  /**
   * 拒绝热点修复候选。
   *
   * @param candidateId 修复候选 ID
   * @return 拒绝结果确认视图
   */
  @PostMapping("/hot-article-repairs/{candidateId}/reject")
  @Operation(summary = "拒绝热点修复候选")
  public ApiResult<HotArticleRepairRejectView> rejectHotArticleRepair(@PathVariable String candidateId) {
    return ApiResult.ok(hotArticleReviews.rejectRepair(candidateId));
  }

  /**
   * 人工裁定热点审核结果。
   *
   * @param reviewId 审稿运行 ID
   * @param body     可选；status 缺省为 approved，comment 缺省空串
   * @return 裁决结果视图
   */
  @PostMapping("/hot-article-reviews/{reviewId}/decision")
  @Operation(summary = "裁定热点审核结果")
  public ApiResult<HotArticleReviewDecisionView> decideHotArticleReview(
      @PathVariable String reviewId,
      @Valid @RequestBody(required = false) HotArticleReviewDecisionRequest body) {
    // status 未传时默认通过，避免前端漏字段导致失败
    String status = body == null || body.getStatus() == null || body.getStatus().isBlank()
        ? HumanReviewStatus.APPROVED.getValue()
        : body.getStatus();
    String comment = body == null || body.getComment() == null ? "" : body.getComment();
    return ApiResult.ok(hotArticleReviews.decide(reviewId, status, comment));
  }
}
