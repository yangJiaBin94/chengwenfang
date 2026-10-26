package com.workbench.quality.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.job.facade.JobFacade;
import com.workbench.job.facade.dto.view.JobView;
import com.workbench.job.facade.enums.JobStatus;
import com.workbench.quality.domain.engine.HotArticleReviewEngine;
import com.workbench.quality.domain.entity.ArticleAuditIssueEntity;
import com.workbench.quality.domain.entity.ArticleAuditRunEntity;
import com.workbench.quality.facade.constant.ArticleAuditDomains;
import com.workbench.quality.facade.constant.AuditIssueMapKeys;
import com.workbench.quality.facade.constant.HotAuditResultKeys;
import com.workbench.quality.facade.dto.view.HotArticleAuditView;
import com.workbench.quality.facade.dto.view.HotArticleReviewEngineResult;
import com.workbench.quality.facade.dto.view.HotArticleReviewItemView;
import com.workbench.quality.facade.enums.ReviewStatus;
import com.workbench.quality.infrastructure.mapper.ArticleAuditIssueMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 热点审稿汇总视图组装器：运行实体 / Job 输出 → {@link HotArticleAuditView}。
 */
@Component
public class HotArticleAuditViewAssembler {
  /** 审稿条目表。 */
  private final ArticleAuditIssueMapper itemMapper;
  /** 热点结果存储（裁决字段与 refs）。 */
  private final HotAuditResultStore auditResultStore;
  /** 任务查询：条目未落库时回退读 Job output。 */
  private final JobFacade jobs;
  /** 规则目录：补全条目分类与引擎类型。 */
  private final HotArticleReviewEngine reviewEngine;

  /**
   * 注入条目 Mapper、结果存储、任务门面与规则引擎。
   *
   * @param itemMapper       条目表 Mapper
   * @param auditResultStore 热点结果存储
   * @param jobs             任务查询门面
   * @param reviewEngine     规则目录
   */
  public HotArticleAuditViewAssembler(
      ArticleAuditIssueMapper itemMapper,
      HotAuditResultStore auditResultStore,
      JobFacade jobs,
      HotArticleReviewEngine reviewEngine) {
    this.itemMapper = itemMapper;
    this.auditResultStore = auditResultStore;
    this.jobs = jobs;
    this.reviewEngine = reviewEngine;
  }

  /**
   * 运行实体转审稿汇总视图（文章 id 由调用方解析）。
   *
   * @param run       审稿运行实体
   * @param articleId 文章 id（API 契约）
   * @return 汇总审计视图
   */
  public HotArticleAuditView toAuditView(ArticleAuditRunEntity run, String articleId) {
    List<ArticleAuditIssueEntity> items = itemMapper.selectList(
        new LambdaQueryWrapper<ArticleAuditIssueEntity>()
            .eq(ArticleAuditIssueEntity::getAuditRunId, run.getId())
            .orderByAsc(ArticleAuditIssueEntity::getSeq)
            .orderByAsc(ArticleAuditIssueEntity::getCreatedAt));
    List<Map<String, Object>> issueMaps = auditResultStore.readIssues(run.getId());
    List<HotArticleReviewItemView> itemViews = new ArrayList<>();
    int passed = 0;
    int warnings = 0;
    int failed = 0;
    int blocking = 0;
    int repairable = 0;
    for (int index = 0; index < items.size(); index++) {
      ArticleAuditIssueEntity item = items.get(index);
      Map<String, Object> raw = index < issueMaps.size() ? issueMaps.get(index) : Map.of();
      HotArticleReviewItemView view = toItemViewFromStored(item, raw);
      String status = item.getStatus() == null ? "" : item.getStatus();
      ReviewStatus parsedStatus = ReviewStatus.from(status);
      if (parsedStatus == ReviewStatus.PASSED) {
        passed++;
      } else if (parsedStatus.isWarnLike()) {
        warnings++;
      } else {
        failed++;
      }
      if (Boolean.TRUE.equals(item.getBlocking()) && ReviewStatus.from(status).isFailLike()) {
        blocking++;
      }
      if (Boolean.TRUE.equals(view.getRepairable())) {
        repairable++;
      }
      itemViews.add(view);
    }

    if (itemViews.isEmpty() && run.getJobId() != null) {
      try {
        JobView job = jobs.get(run.getJobId());
        if (job.getOutput() != null && job.getOutput().get(HotAuditResultKeys.RESULTS) instanceof List<?>) {
          return mergeJobOutput(run, job, articleId);
        }
        if (!JobStatus.SUCCEEDED.getValue().equals(job.getStatus())
            && !JobStatus.FAILED.getValue().equals(job.getStatus())) {
          HotArticleAuditView pending = baseView(run, articleId);
          pending.setStatus(ReviewStatus.PENDING.getValue());
          pending.setRuleStatus(ReviewStatus.PENDING.getValue());
          pending.setSummary("审核进行中");
          return pending;
        }
        HotArticleAuditView incomplete = baseView(run, articleId);
        incomplete.setStatus(ReviewStatus.FAILED.getValue());
        incomplete.setRuleStatus(ReviewStatus.FAILED.getValue());
        incomplete.setFailed(1);
        incomplete.setFailedCount(1);
        incomplete.setSummary("审核条目未完整落库，请点击「重新审核」");
        return incomplete;
      } catch (Exception ignored) {
        // 回退到库内条目汇总
      }
    }

    HotArticleAuditView out = baseView(run, articleId);
    String reviewStatus = run.getStatus() == null ? ReviewStatus.PENDING.getValue() : run.getStatus();
    if (itemViews.isEmpty() && ReviewStatus.from(reviewStatus) == ReviewStatus.PASSED) {
      reviewStatus = ReviewStatus.PENDING.getValue();
    }
    out.setStatus(reviewStatus);
    out.setRuleStatus(reviewStatus);
    out.setItems(itemViews);
    out.setResults(itemViews);
    out.setPassedCount(passed);
    out.setWarningCount(warnings);
    out.setFailedCount(failed);
    out.setBlockingCount(blocking);
    out.setRepairableCount(repairable);
    out.setPassed(passed);
    out.setWarnings(warnings);
    out.setFailed(failed);
    out.setBlockingFailed(Boolean.TRUE.equals(run.getBlockingFailed()) || blocking > 0);
    out.setSummary(itemViews.isEmpty()
        ? "暂无审核条目，请重新审核"
        : blocking > 0
            ? "审核发现阻断问题"
            : (failed > 0 ? "审核有告警/失败项" : "自动审核通过"));
    if (StrUtil.isNotBlank(run.getDecisionStatus())) {
      out.setDecisionStatus(run.getDecisionStatus());
      out.setDecisionComment(run.getDecisionComment());
      out.setDecidedAt(run.getDecidedAt());
    }
    return out;
  }

  /**
   * 尚无审稿记录时的空视图。
   *
   * @param articleId 文章 ID
   * @return status=none 的占位视图
   */
  public HotArticleAuditView noneStub(String articleId) {
    return HotArticleAuditView.builder()
        .id(null)
        .articleId(articleId)
        .contentHash("")
        .status(ArticleAuditDomains.STATUS_NONE)
        .ruleStatus(ArticleAuditDomains.STATUS_NONE)
        .aiStatus(ArticleAuditDomains.AI_STATUS_SKIPPED)
        .passedCount(0)
        .warningCount(0)
        .failedCount(0)
        .blockingCount(0)
        .repairableCount(0)
        .passed(0)
        .warnings(0)
        .failed(0)
        .summary("尚未审核")
        .items(List.of())
        .results(List.of())
        .build();
  }

  /**
   * 从 Job 输出合并审稿视图（条目尚未落库时）。
   *
   * @param run       审稿运行实体
   * @param job       关联 Job
   * @param articleId 文章 id
   * @return 合并后的汇总视图
   */
  private HotArticleAuditView mergeJobOutput(
      ArticleAuditRunEntity run, JobView job, String articleId) {
    HotArticleAuditView out = baseView(run, articleId);
    Map<String, Object> output = job.getOutput() == null ? Map.of() : job.getOutput();
    List<Map<String, Object>> results =
        HotArticleReviewEngineResult.normalizeResultRows(output.get(HotAuditResultKeys.RESULTS));
    List<HotArticleReviewItemView> items = new ArrayList<>();
    List<HotArticleReviewItemView> resultViews = new ArrayList<>();
    int passed = 0;
    int warnings = 0;
    int failed = 0;
    int blocking = 0;
    int repairable = 0;
    for (Map<String, Object> raw : results) {
      HotArticleReviewItemView item = toItemView(raw);
      String status = item.getStatus() == null ? "" : item.getStatus();
      ReviewStatus parsedStatus = ReviewStatus.from(status);
      if (parsedStatus == ReviewStatus.PASSED) {
        passed++;
        item.setStatus(ReviewStatus.PASSED.getValue());
      } else if (parsedStatus.isWarnLike()) {
        warnings++;
        item.setStatus(ReviewStatus.WARN.getValue());
      } else {
        failed++;
        item.setStatus(ReviewStatus.FAILED.getValue());
      }
      if (Boolean.TRUE.equals(item.getBlocking())
          && ReviewStatus.from(String.valueOf(item.getStatus())).isFailLike()) {
        blocking++;
      }
      if (Boolean.TRUE.equals(item.getRepairable())) {
        repairable++;
      }
      if (item.getId() == null) {
        item.setId(item.getCode());
      }
      items.add(item);
      resultViews.add(toItemView(raw));
    }
    String reviewStatus = String.valueOf(output.getOrDefault(
        HotAuditResultKeys.STATUS,
        blocking > 0 ? ReviewStatus.FAILED.getValue() : ReviewStatus.PASSED.getValue()));
    out.setStatus(reviewStatus);
    out.setRuleStatus(reviewStatus);
    out.setItems(items);
    out.setResults(resultViews);
    out.setPassedCount(passed);
    out.setWarningCount(warnings);
    out.setFailedCount(failed);
    out.setBlockingCount(blocking);
    out.setRepairableCount(repairable);
    out.setPassed(passed);
    out.setWarnings(warnings);
    out.setFailed(failed);
    out.setBlockingFailed(
        Boolean.TRUE.equals(output.get(HotAuditResultKeys.BLOCKING_FAILED)) || blocking > 0);
    out.setSummary(blocking > 0 ? "审核发现阻断问题" : (failed > 0 ? "审核有告警/失败项" : "自动审核通过"));
    return out;
  }

  /**
   * 库内问题 + refs Map → 条目视图。
   *
   * @param item 问题行
   * @param raw  含 locations/evidence 的 Map
   * @return 条目视图
   */
  @SuppressWarnings("unchecked")
  private HotArticleReviewItemView toItemViewFromStored(
      ArticleAuditIssueEntity item, Map<String, Object> raw) {
    HotArticleReviewEngine.ReviewItemDef definition = reviewEngine.definition(item.getCode());
    String engine = StrUtil.blankToDefault(
        item.getEngine(),
        definition == null ? ArticleAuditDomains.ENGINE_RULE : definition.engine());
    String category = item.getCategory() != null
        ? item.getCategory()
        : (definition == null ? null : definition.category());
    // 汇总视图 severity 仍按 blocking 派生（与原 article_audit_issues 无 severity 列时一致）
    String severity = Boolean.TRUE.equals(item.getBlocking())
        ? ArticleAuditDomains.SEVERITY_BLOCKING
        : ArticleAuditDomains.SEVERITY_INFO;
    Boolean repairable = item.getRepairable() != null
        ? item.getRepairable()
        : (definition != null && definition.repairable());
    List<Map<String, Object>> locations = raw.get(AuditIssueMapKeys.LOCATIONS) instanceof List<?> list
        ? (List<Map<String, Object>>) (List<?>) list
        : null;
    List<Map<String, Object>> evidence = raw.get(AuditIssueMapKeys.EVIDENCE) instanceof List<?> list
        ? (List<Map<String, Object>>) (List<?>) list
        : null;
    return HotArticleReviewItemView.builder()
        .id(item.getCode())
        .code(item.getCode())
        .engine(engine)
        .category(category)
        .severity(severity)
        .title(item.getTitle())
        .status(item.getStatus())
        .blocking(Boolean.TRUE.equals(item.getBlocking()))
        .detail(item.getDetail())
        .locations(locations)
        .evidence(evidence)
        .repairable(repairable)
        .requiresHumanConfirmation(item.getRequiresHumanConfirmation())
        .build();
  }

  /**
   * Job 输出 Map 条目转视图（只拷贝已知字段）。
   *
   * @param raw 引擎/Job 输出中的单条规则结果
   * @return 条目视图
   */
  @SuppressWarnings("unchecked")
  private HotArticleReviewItemView toItemView(Map<String, Object> raw) {
    Map<String, Object> src = raw == null ? Map.of() : raw;
    Object idObj = src.get(AuditIssueMapKeys.ID);
    Object codeObj = src.get(AuditIssueMapKeys.CODE);
    String code = codeObj == null
        ? (idObj == null ? null : String.valueOf(idObj))
        : String.valueOf(codeObj);
    String id = idObj == null ? code : String.valueOf(idObj);
    List<Map<String, Object>> locations = src.get(AuditIssueMapKeys.LOCATIONS) instanceof List<?> list
        ? (List<Map<String, Object>>) (List<?>) list
        : null;
    List<Map<String, Object>> evidence = src.get(AuditIssueMapKeys.EVIDENCE) instanceof List<?> list
        ? (List<Map<String, Object>>) (List<?>) list
        : null;
    return HotArticleReviewItemView.builder()
        .id(id)
        .code(code)
        .engine(asString(src.get(AuditIssueMapKeys.ENGINE)))
        .category(asString(src.get(AuditIssueMapKeys.CATEGORY)))
        .severity(asString(src.get(AuditIssueMapKeys.SEVERITY)))
        .title(asString(src.get(AuditIssueMapKeys.TITLE)))
        .detail(asString(src.get(AuditIssueMapKeys.DETAIL) != null
            ? src.get(AuditIssueMapKeys.DETAIL)
            : src.get(AuditIssueMapKeys.MESSAGE)))
        .location(asString(src.get("location")))
        .locations(locations)
        .evidence(evidence)
        .repairable(src.get(AuditIssueMapKeys.REPAIRABLE) instanceof Boolean bool ? bool : null)
        .requiresHumanConfirmation(
            src.get(AuditIssueMapKeys.REQUIRES_HUMAN_CONFIRMATION) instanceof Boolean bool
                ? bool
                : null)
        .repairStatus(asString(src.get("repairStatus")))
        .repairInstruction(asString(src.get("repairInstruction")))
        .blocking(Boolean.TRUE.equals(src.get(AuditIssueMapKeys.BLOCKING)))
        .status(asString(src.get(AuditIssueMapKeys.STATUS)))
        .waiverReason(asString(src.get("waiverReason")))
        .build();
  }

  /**
   * 将任意对象安全转为字符串；null 仍为 null。
   *
   * @param value 任意值
   * @return 字符串或 null
   */
  private static String asString(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  /**
   * 审稿汇总基础字段（计数清零，items 空列表）。
   *
   * @param run       运行实体
   * @param articleId 文章 id
   * @return 基础视图骨架
   */
  private HotArticleAuditView baseView(ArticleAuditRunEntity run, String articleId) {
    return HotArticleAuditView.builder()
        .id(run.getId())
        .articleId(articleId)
        .contentHash(run.getContentHash() == null ? "" : run.getContentHash())
        .jobId(run.getJobId() == null ? run.getId() : run.getJobId())
        .standardVersion(
            run.getStandardVersion() == null
                ? ArticleAuditDomains.STANDARD_HOT_NEWS_V1
                : run.getStandardVersion())
        .createdAt(run.getCreatedAt())
        .aiStatus(ArticleAuditDomains.AI_STATUS_SKIPPED)
        .items(List.of())
        .results(List.of())
        .passedCount(0)
        .warningCount(0)
        .failedCount(0)
        .blockingCount(0)
        .repairableCount(0)
        .passed(0)
        .warnings(0)
        .failed(0)
        .build();
  }
}
