package com.chengwenfang.quality.facade;


import com.chengwenfang.quality.facade.dto.request.AuditContextRequest;
import com.chengwenfang.quality.facade.dto.request.HotArticleReviewSnapshotRequest;
import com.chengwenfang.quality.facade.dto.view.AuditResultView;
import com.chengwenfang.quality.facade.dto.view.HotArticleRuleResultView;
import com.chengwenfang.quality.facade.dto.view.KnowledgeRuleView;
import java.util.List;

/** 文章审核门面：对正文执行确定性规则审核。 */
public interface AuditFacade {
  /**
   * 审核给定正文，返回规则结果汇总。
   *
   * @param text 待审核 Markdown/正文
   * @return 含规则列表与是否通过等信息
   */
  AuditResultView auditText(String text);

  /**
   * 按完整上下文审核（正文 + 配图资产等）。
   *
   * @param request 审核上下文；null 等价于空正文
   * @return 含规则列表与是否通过等信息
   */
  AuditResultView auditContext(AuditContextRequest request);

  /**
   * 知识库一致性规则检查。
   *
   * @param article 公众号正文
   * @param source 可选课程原稿
   * @return 每项含 code / passed / detail / blocking
   */
  List<KnowledgeRuleView> checkKnowledge(String article, String source);

  /** 是否存在未使用引用块样式的独立公式。 */
  boolean hasUnstyledFormulaBlocks(String text);

  /** 是否残留 LaTeX 排版命令。 */
  boolean hasLatexFormulaCommands(String text);

  /**
   * 将独立公式规范为引用块样式，并尽量去掉 LaTeX 排版命令。
   *
   * @param text 待规范化正文
   * @return 规范化后的正文
   */
  String normalizeFormulaBlocks(String text);

  /**
   * 热点文章规则审稿。
   *
   * @param snapshot 含 content / contentHash / sources / standardVersion
   * @return 规则结果列表
   */
  List<HotArticleRuleResultView> runHotArticleRuleReview(HotArticleReviewSnapshotRequest snapshot);

  /**
   * 将课程确定性审核结果落库到 {@code audit_runs} / {@code audit_issues}。
   *
   * @param articleId  文章/壳 id
   * @param documentId 统一文档 id；可空
   * @param result     引擎结果；null 不写
   * @return 新建运行 id；未写库时为空
   */
  String persistCourseAudit(String articleId, String documentId, AuditResultView result);
}
