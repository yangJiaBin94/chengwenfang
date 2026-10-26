package com.chengwenfang.quality.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.quality.domain.entity.ArticleAuditIssueEntity;
import com.chengwenfang.quality.domain.entity.ArticleAuditRunEntity;
import com.chengwenfang.quality.facade.constant.CourseAuditSummaryKeys;
import com.chengwenfang.quality.facade.dto.view.AuditItemView;
import com.chengwenfang.quality.facade.dto.view.AuditResultView;
import com.chengwenfang.quality.facade.enums.ReviewStatus;
import com.chengwenfang.quality.infrastructure.mapper.ArticleAuditIssueMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 课程审核汇总：score / warning_count / failed_count / passed / blocking_failed ↔ Map。
 */
@Component
@RequiredArgsConstructor
public class CourseAuditSummaryStore {
  private final ArticleAuditIssueMapper issueMapper;

  /**
   * 将课程审核分数与计数写入运行列（不写 issues；调用方须先 insert 运行行）。
   *
   * @param run    运行行
   * @param result 引擎结果
   */
  public void applyColumns(ArticleAuditRunEntity run, AuditResultView result) {
    if (run == null || result == null) {
      return;
    }
    boolean blocking = result.isBlocking() || result.getFailed() > 0;
    run.setScore(result.getScore());
    run.setPassed(!blocking && result.getFailed() == 0);
    run.setWarningCount(result.getWarnings());
    run.setFailedCount(result.getFailed());
    run.setBlockingFailed(blocking);
  }

  /**
   * 将课程审核结果写入运行列与 issues（运行行须已 insert）。
   *
   * @param run    运行行（须有 id 且已落库）
   * @param result 引擎结果
   * @param now    落库时间
   */
  @Transactional
  public void write(ArticleAuditRunEntity run, AuditResultView result, LocalDateTime now) {
    if (run == null || result == null || StrUtil.isBlank(run.getId())) {
      return;
    }
    applyColumns(run, result);
    replaceIssues(run.getId(), result.getItems(), now);
  }

  /**
   * 从运行列与 issues 重建汇总 Map（passed 为通过项计数）。
   *
   * @param run 运行行
   * @return 汇总 Map
   */
  public Map<String, Object> read(ArticleAuditRunEntity run) {
    Map<String, Object> summary = new LinkedHashMap<>();
    if (run == null) {
      return summary;
    }
    summary.put(CourseAuditSummaryKeys.SCORE, run.getScore() == null ? 0 : run.getScore());
    int passedCount = countPassedIssues(run.getId());
    summary.put(CourseAuditSummaryKeys.PASSED, passedCount);
    summary.put(
        CourseAuditSummaryKeys.WARNINGS,
        run.getWarningCount() == null ? 0 : run.getWarningCount());
    summary.put(
        CourseAuditSummaryKeys.FAILED,
        run.getFailedCount() == null ? 0 : run.getFailedCount());
    summary.put(CourseAuditSummaryKeys.BLOCKING, Boolean.TRUE.equals(run.getBlockingFailed()));
    return summary;
  }

  /**
   * 全量替换课程 issues。
   *
   * @param auditRunId 运行 id
   * @param items      检查项
   * @param now        创建时间
   */
  @Transactional
  public void replaceIssues(String auditRunId, List<AuditItemView> items, LocalDateTime now) {
    if (StrUtil.isBlank(auditRunId)) {
      return;
    }
    issueMapper.delete(new LambdaQueryWrapper<ArticleAuditIssueEntity>()
        .eq(ArticleAuditIssueEntity::getAuditRunId, auditRunId));
    if (items == null || items.isEmpty()) {
      return;
    }
    LocalDateTime createdAt = now == null ? LocalDateTime.now() : now;
    int seq = 0;
    for (AuditItemView item : items) {
      if (item == null || StrUtil.isBlank(item.getCode())) {
        continue;
      }
      ArticleAuditIssueEntity issue = new ArticleAuditIssueEntity();
      issue.setId(IdsUtil.newId());
      issue.setAuditRunId(auditRunId);
      issue.setSeq(seq++);
      issue.setCode(item.getCode());
      issue.setTitle(StrUtil.blankToDefault(item.getTitle(), item.getCode()));
      issue.setStatus(normalizeCourseItemStatus(item.getStatus()));
      issue.setBlocking(Boolean.TRUE.equals(item.getBlocking()));
      issue.setDetail(StrUtil.nullToEmpty(item.getDetail()));
      issue.setCreatedAt(createdAt);
      issueMapper.insert(issue);
    }
  }

  /**
   * 统计通过项数量。
   *
   * @param auditRunId 运行 id
   * @return 通过数
   */
  private int countPassedIssues(String auditRunId) {
    if (StrUtil.isBlank(auditRunId)) {
      return 0;
    }
    Long count = issueMapper.selectCount(new LambdaQueryWrapper<ArticleAuditIssueEntity>()
        .eq(ArticleAuditIssueEntity::getAuditRunId, auditRunId)
        .eq(ArticleAuditIssueEntity::getStatus, ReviewStatus.PASSED.getValue()));
    return count == null ? 0 : count.intValue();
  }

  /**
   * 课程引擎 status 归一到统一词表。
   *
   * @param raw 引擎原始状态
   * @return passed / warn / failed
   */
  private static String normalizeCourseItemStatus(String raw) {
    ReviewStatus parsed = ReviewStatus.from(raw);
    if (parsed == ReviewStatus.PASSED) {
      return ReviewStatus.PASSED.getValue();
    }
    if (parsed.isWarnLike()) {
      return ReviewStatus.WARN.getValue();
    }
    return ReviewStatus.FAILED.getValue();
  }
}
