package com.chengwenfang.quality.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.quality.domain.engine.CourseAuditEngine;
import com.chengwenfang.quality.domain.engine.KnowledgeRulesEngine;
import com.chengwenfang.quality.domain.engine.HotArticleReviewEngine;
import com.chengwenfang.quality.domain.entity.ArticleAuditRunEntity;
import com.chengwenfang.quality.facade.AuditFacade;
import com.chengwenfang.quality.facade.constant.ArticleAuditDomains;
import com.chengwenfang.quality.facade.dto.view.AuditIssueView;
import com.chengwenfang.quality.facade.dto.view.AuditItemView;
import com.chengwenfang.quality.facade.dto.view.AuditResultView;
import com.chengwenfang.quality.facade.dto.request.AuditContextRequest;
import com.chengwenfang.quality.facade.dto.request.HotArticleReviewSnapshotRequest;
import com.chengwenfang.quality.facade.dto.view.HotArticleRuleResultView;
import com.chengwenfang.quality.facade.dto.view.KnowledgeRuleView;
import com.chengwenfang.quality.facade.enums.ReviewStatus;
import com.chengwenfang.quality.infrastructure.mapper.ArticleAuditRunMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link AuditFacade} 实现：委托课程审核、知识库与热点文章规则引擎；
 * 课程确定性结果落库 {@code audit_runs} / {@code audit_issues}。
 */
@Slf4j
@Service
public class AuditFacadeImpl implements AuditFacade {
  /** 课程正文审核引擎（结构/格式等通用检查）。 */
  private final CourseAuditEngine courseAudit;
  /** 知识库一致性规则（公式样式、LaTeX 残留等）。 */
  private final KnowledgeRulesEngine knowledgeRules;
  /** 热点文章专用规则审稿引擎。 */
  private final HotArticleReviewEngine hotArticleReview;
  /** 统一审核运行表。 */
  private final ArticleAuditRunMapper auditRunMapper;
  /** 课程汇总列/issues 存储。 */
  private final CourseAuditSummaryStore courseSummaryStore;

  /**
   * 注入规则引擎与统一审核表依赖。
   *
   * @param courseAudit         课程审核引擎
   * @param knowledgeRules      知识库规则引擎
   * @param hotArticleReview    热点文章规则引擎
   * @param auditRunMapper      审核运行表
   * @param courseSummaryStore  课程汇总存储
   */
  public AuditFacadeImpl(
      CourseAuditEngine courseAudit,
      KnowledgeRulesEngine knowledgeRules,
      HotArticleReviewEngine hotArticleReview,
      ArticleAuditRunMapper auditRunMapper,
      CourseAuditSummaryStore courseSummaryStore) {
    this.courseAudit = courseAudit;
    this.knowledgeRules = knowledgeRules;
    this.hotArticleReview = hotArticleReview;
    this.auditRunMapper = auditRunMapper;
    this.courseSummaryStore = courseSummaryStore;
  }

  /**
   * 审核给定正文：先跑课程规则，再合并知识库规则，汇总打分。
   *
   * @param text 待审核 Markdown/正文；可为 null（引擎侧按空串处理）
   * @return 含 score、通过/告警/失败计数、issues 与全量 items
   */
  @Override
  public AuditResultView auditText(String text) {
    return auditContext(AuditContextRequest.builder()
        .article(text)
        .draft("")
        .imageAssets(List.of())
        .imagePublicPaths(List.of())
        .currentVersionSaved(true)
        .articleFilePath(null)
        .build());
  }

  /**
   * 按完整上下文审核：课程规则 + 知识库规则。
   *
   * @param request 正文与配图等上下文；null 按空正文处理
   * @return 含 score、通过/告警/失败计数、issues 与全量 items
   */
  @Override
  public AuditResultView auditContext(AuditContextRequest request) {
    AuditContextRequest src = request == null ? AuditContextRequest.builder().build() : request;
    String article = StrUtil.nullToEmpty(src.getArticle());
    String draft = StrUtil.nullToEmpty(src.getDraft());
    List<Map<String, Object>> imageAssets = ObjectUtil.defaultIfNull(src.getImageAssets(), List.of());
    List<String> imagePublicPaths = ObjectUtil.defaultIfNull(src.getImagePublicPaths(), List.of());
    boolean versionSaved = src.getCurrentVersionSaved() == null || Boolean.TRUE.equals(src.getCurrentVersionSaved());
    CourseAuditEngine.AuditResult course = courseAudit.run(new CourseAuditEngine.AuditContext(
        article,
        draft,
        imageAssets,
        imagePublicPaths,
        versionSaved,
        src.getArticleFilePath()));
    List<CourseAuditEngine.AuditCheck> merged = new ArrayList<>(course.items());
    for (KnowledgeRulesEngine.KnowledgeRule rule : knowledgeRules.check(article, draft)) {
      String status = rule.passed() ? "pass" : (rule.blocking() ? "fail" : "warn");
      merged.add(new CourseAuditEngine.AuditCheck(
          rule.code(),
          rule.code(),
          rule.detail(),
          status,
          true,
          null,
          rule.passed() ? null : rule.detail()));
    }
    int failed = (int) merged.stream().filter(check -> "fail".equals(check.status())).count();
    int warnings = (int) merged.stream().filter(check -> "warn".equals(check.status())).count();
    int passed = (int) merged.stream().filter(check -> "pass".equals(check.status())).count();
    int score = Math.max(0, 100 - failed * 18 - warnings * 7);
    return toAuditResultView(new CourseAuditEngine.AuditResult(score, passed, warnings, failed, merged));
  }

  /**
   * 知识库一致性规则检查（不走课程引擎）。
   *
   * @param article 公众号正文（或待检稿）
   * @param source  可选课程原稿，用于对照一致性
   * @return 每项含 code / passed / detail / blocking
   */
  @Override
  public List<KnowledgeRuleView> checkKnowledge(String article, String source) {
    List<KnowledgeRuleView> out = new ArrayList<>();
    for (KnowledgeRulesEngine.KnowledgeRule rule : knowledgeRules.check(article, source)) {
      out.add(KnowledgeRuleView.builder()
          .code(rule.code())
          .passed(rule.passed())
          .detail(rule.detail())
          .blocking(rule.blocking())
          .build());
    }
    return out;
  }

  /**
   * 判断正文是否存在未使用引用块样式的独立公式。
   *
   * @param text 待检测正文
   * @return true 表示存在未样式化公式块
   */
  @Override
  public boolean hasUnstyledFormulaBlocks(String text) {
    return knowledgeRules.hasUnstyledFormulaBlocks(text);
  }

  /**
   * 判断正文是否残留 LaTeX 排版命令。
   *
   * @param text 待检测正文
   * @return true 表示仍含 LaTeX 命令痕迹
   */
  @Override
  public boolean hasLatexFormulaCommands(String text) {
    return knowledgeRules.hasLatexFormulaCommands(text);
  }

  @Override
  public String normalizeFormulaBlocks(String text) {
    return knowledgeRules.normalizeFormulaBlocks(text);
  }

  /**
   * 热点文章规则审稿：将快照字段转 Map 后交给热点引擎。
   *
   * @param snapshot 含 content / contentHash / sources / standardVersion；null 视为空快照
   * @return 规则结果列表；未知字段收入 extras
   */
  @Override
  public List<HotArticleRuleResultView> runHotArticleRuleReview(HotArticleReviewSnapshotRequest snapshot) {
    Map<String, Object> raw = new LinkedHashMap<>();
    if (snapshot != null) {
      raw.put("content", snapshot.getContent() == null ? "" : snapshot.getContent());
      raw.put("contentHash", snapshot.getContentHash() == null ? "" : snapshot.getContentHash());
      raw.put("sources", snapshot.getSources() == null ? List.of() : snapshot.getSources());
      raw.put("standardVersion", snapshot.getStandardVersion() == null
          ? ArticleAuditDomains.STANDARD_HOT_NEWS_V1
          : snapshot.getStandardVersion());
    }
    List<HotArticleRuleResultView> out = new ArrayList<>();
    for (Map<String, Object> item : hotArticleReview.runRuleReview(raw)) {
      Map<String, Object> extras = new LinkedHashMap<>(item);
      extras.remove("code");
      extras.remove("status");
      extras.remove("blocking");
      extras.remove("detail");
      extras.remove("title");
      out.add(HotArticleRuleResultView.builder()
          .code(item.get("code") == null ? null : String.valueOf(item.get("code")))
          .status(item.get("status") == null ? null : String.valueOf(item.get("status")))
          .blocking(item.get("blocking") instanceof Boolean bool ? bool : null)
          .detail(item.get("detail") == null ? null : String.valueOf(item.get("detail")))
          .title(item.get("title") == null ? null : String.valueOf(item.get("title")))
          .extras(extras)
          .build());
    }
    return out;
  }

  /**
   * 课程确定性审核落库：新建 run + 全量替换 issues。
   *
   * @param articleId  文章 id
   * @param documentId 统一文档/产出 id
   * @param result     引擎结果
   * @return 运行 id；result 为空时返回空串
   */
  @Override
  @Transactional
  public String persistCourseAudit(String articleId, String documentId, AuditResultView result) {
    if (result == null) {
      return "";
    }
    String runId = IdsUtil.newId();
    LocalDateTime now = LocalDateTime.now();
    boolean blocking = result.isBlocking() || result.getFailed() > 0;
    ReviewStatus runStatus = blocking ? ReviewStatus.FAILED : ReviewStatus.PASSED;

    ArticleAuditRunEntity run = new ArticleAuditRunEntity();
    run.setId(runId);
    run.setOutputId(StrUtil.blankToDefault(documentId, articleId));
    run.setStatus(runStatus.getValue());
    run.setMode(ArticleAuditDomains.MODE_DETERMINISTIC);
    run.setContentHash("");
    run.setStandardVersion(ArticleAuditDomains.STANDARD_COURSE_V1);
    run.setCreatedAt(now);
    run.setFinishedAt(now);
    // 先写主行列再 insert，issues 依赖 audit_runs FK
    courseSummaryStore.applyColumns(run, result);
    auditRunMapper.insert(run);
    courseSummaryStore.replaceIssues(runId, result.getItems(), now);

    log.info(
        "课程审核已落库 runId={} articleId={} documentId={} items={} blocking={}",
        runId,
        articleId,
        documentId,
        CollUtil.size(result.getItems()),
        blocking);
    return runId;
  }

  /**
   * 将课程引擎结果转为对外 {@link AuditResultView}（拆分 issues 与全量 items）。
   *
   * @param result 课程引擎汇总结果（含打分与检查项）
   * @return Facade 层审核结果视图；任一 fail 则 blocking=true
   */
  private AuditResultView toAuditResultView(CourseAuditEngine.AuditResult result) {
    List<AuditIssueView> issues = result.items().stream()
        .filter(item -> !"pass".equals(item.status()))
        .map(item -> AuditIssueView.builder()
            .code(item.code())
            .title(item.title())
            .severity(item.status())
            .message(item.detail())
            .blocking(item.blocking())
            .fixableByTextAi(item.fixableByTextAi())
            .location(item.location())
            .suggestion(item.suggestion())
            .build())
        .toList();
    List<AuditItemView> items = result.items().stream()
        .map(item -> AuditItemView.builder()
            .code(item.code())
            .title(item.title())
            .detail(item.detail())
            .status(item.status())
            .fixableByTextAi(item.fixableByTextAi())
            .location(item.location())
            .suggestion(item.suggestion())
            .blocking(item.blocking())
            .build())
        .toList();
    return AuditResultView.builder()
        .score(result.score())
        .blocking(result.failed() > 0)
        .passed(result.passed())
        .warnings(result.warnings())
        .failed(result.failed())
        .issues(issues)
        .items(items)
        .build();
  }
}
