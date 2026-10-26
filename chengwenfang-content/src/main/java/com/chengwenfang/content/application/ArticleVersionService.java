package com.chengwenfang.content.application;

import com.chengwenfang.content.domain.entity.ArticleEntity;
import com.chengwenfang.content.domain.entity.ArticleVersionEntity;
import com.chengwenfang.content.facade.dto.request.ArticleUpdateRequest;
import com.chengwenfang.content.facade.dto.view.ArticleVersionView;
import com.chengwenfang.content.facade.dto.view.ArticleView;
import com.chengwenfang.content.facade.dto.view.CourseGenerationCommitView;
import com.chengwenfang.content.facade.dto.view.RepairResponseView;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文章读写、版本、生成提交与修复流水线（委派门面）。
 */
@Service
public class ArticleVersionService {
  private final OutputVersionService versions;
  private final OutputContentSaveService contentSave;
  private final ArticleRepairService repairs;

  /**
   * @param versions    版本列表与恢复
   * @param contentSave 正文保存
   * @param repairs     修复流水线
   */
  public ArticleVersionService(
      OutputVersionService versions,
      OutputContentSaveService contentSave,
      ArticleRepairService repairs) {
    this.versions = versions;
    this.contentSave = contentSave;
    this.repairs = repairs;
  }

  /**
   * 列出文章历史版本。
   *
   * @param articleId  文章 id
   * @param documentId 统一文档 id；空则按文章解析
   * @return 版本视图列表
   */
  public List<ArticleVersionView> listArticleVersions(String articleId, String documentId) {
    return versions.listArticleVersions(articleId, documentId);
  }

  /**
   * 列出文章历史版本（自动解析 documentId）。
   *
   * @param articleId 文章 id
   * @return 版本视图列表
   */
  public List<ArticleVersionView> listArticleVersions(String articleId) {
    return versions.listArticleVersions(articleId);
  }

  /**
   * 将文章内容回滚到指定版本，并新增一条 restore 来源版本。
   *
   * @param articleId  文章 id
   * @param versionId  目标历史版本 id
   * @param documentId 统一文档 id；空则按文章解析
   * @return 恢复后的文章视图
   */
  @Transactional
  public ArticleView restoreArticleVersion(String articleId, String versionId, String documentId) {
    return versions.restoreArticleVersion(articleId, versionId, documentId);
  }

  /**
   * 将文章内容回滚到指定版本（自动解析 documentId）。
   *
   * @param articleId 文章 id
   * @param versionId 目标历史版本 id
   * @return 恢复后的文章视图
   */
  @Transactional
  public ArticleView restoreArticleVersion(String articleId, String versionId) {
    return versions.restoreArticleVersion(articleId, versionId);
  }

  /**
   * 获取文章详情。
   *
   * @param articleId 文章 id
   * @return 文章视图
   */
  public ArticleView getArticle(String articleId) {
    return contentSave.getArticle(articleId);
  }

  /**
   * 更新文章标题/正文；带 versionToken 乐观锁，正文变更时写版本。
   *
   * @param articleId 文章 id
   * @param body      更新请求；null 按空对象
   * @return 更新后的文章视图
   */
  @Transactional
  public ArticleView updateArticle(String articleId, ArticleUpdateRequest body) {
    return contentSave.updateArticle(articleId, body);
  }

  /**
   * 提交课程生成结果，写入原稿与公众号正文并将课程标为已生成。
   *
   * @param courseId       课程 id
   * @param draftMarkdown  原稿 Markdown
   * @param wechatMarkdown 公众号稿 Markdown
   * @return 提交结果（含两篇文章视图）
   */
  @Transactional
  public CourseGenerationCommitView commitCourseGeneration(String courseId, String draftMarkdown, String wechatMarkdown) {
    return contentSave.commitCourseGeneration(courseId, draftMarkdown, wechatMarkdown);
  }

  /**
   * 写入正文并用指定版本号插入快照（同一次生成的原稿与公众号共用 version_no）。
   *
   * @param article   文章实体
   * @param markdown  Markdown 正文
   * @param now       时间戳
   * @param versionNo 版本号
   * @param source    来源
   * @param reason    原因
   * @return 已就地更新的文章实体
   */
  public ArticleEntity applyMarkdownWithVersion(
      ArticleEntity article,
      String markdown,
      LocalDateTime now,
      int versionNo,
      String source,
      String reason) {
    return contentSave.applyMarkdownWithVersion(article, markdown, now, versionNo, source, reason);
  }

  /**
   * 按文章类型写入生成结果；articleId 也可传 courseId 再按 kind 解析目标文章。
   *
   * @param articleId 文章 id，或课程 id（文章不存在时按课壳解析）
   * @param kind      期望 kind；可与文章现有 kind 校验
   * @param markdown  生成正文
   * @return 写入后的文章视图
   */
  @Transactional
  public ArticleView generateArticleKind(String articleId, String kind, String markdown) {
    return contentSave.generateArticleKind(articleId, kind, markdown);
  }

  /**
   * 启动文章修复异步任务。
   *
   * @param articleId  文章 id
   * @param documentId 统一文档 id；空则按课程文章解析
   * @return 新建任务 id
   */
  public String startRepair(String articleId, String documentId) {
    return repairs.startRepair(articleId, documentId);
  }

  /**
   * 启动文章修复异步任务（自动解析 documentId）。
   *
   * @param articleId 文章 id
   * @return 新建任务 id
   */
  public String startRepair(String articleId) {
    return repairs.startRepair(articleId);
  }

  /**
   * 补齐配图元数据（绑定 articleId / 缺省名称），并可选清理夹具符号。
   *
   * @param articleId 文章 id
   * @return 修复后（或未变）的文章视图
   */
  @Transactional
  public ArticleView repairMetadata(String articleId) {
    return repairs.repairMetadata(articleId);
  }

  /**
   * 应用 AI/修复流水线结果：前后版本快照 + repair_runs 记录。
   *
   * @param articleId       文章 id
   * @param jobId           触发修复的任务 id
   * @param repairedContent 修复后正文
   * @param beforeAudit     修复前审核结果 Map
   * @param afterAudit      修复后审核结果 Map
   * @param resolvedCodes   已解决的问题码列表
   * @param addedCodes      新增的问题码列表
   * @return 修复响应视图（含前后版本 id 与分数）
   */
  @Transactional
  public RepairResponseView applyArticleRepair(
      String articleId,
      String jobId,
      String repairedContent,
      Map<String, Object> beforeAudit,
      Map<String, Object> afterAudit,
      List<String> resolvedCodes,
      List<String> addedCodes) {
    return repairs.applyArticleRepair(
        articleId, jobId, repairedContent, beforeAudit, afterAudit, resolvedCodes, addedCodes);
  }

  /**
   * 应用 AI/修复流水线结果：前后版本快照 + repair_runs 记录。
   *
   * @param articleId       文章 id
   * @param jobId           触发修复的任务 id
   * @param repairedContent 修复后正文
   * @param beforeAudit     修复前审核结果 Map
   * @param afterAudit      修复后审核结果 Map
   * @param resolvedCodes   已解决的问题码列表
   * @param addedCodes      新增的问题码列表
   * @param documentId      统一文档 id；空则按课程文章解析
   * @return 修复响应视图（含前后版本 id 与分数）
   */
  @Transactional
  public RepairResponseView applyArticleRepair(
      String articleId,
      String jobId,
      String repairedContent,
      Map<String, Object> beforeAudit,
      Map<String, Object> afterAudit,
      List<String> resolvedCodes,
      List<String> addedCodes,
      String documentId) {
    return repairs.applyArticleRepair(
        articleId, jobId, repairedContent, beforeAudit, afterAudit, resolvedCodes, addedCodes, documentId);
  }

  /**
   * 将 Markdown 写入文章字段并插入 generation 版本。
   *
   * @param article  目标文章（须已存在）
   * @param markdown 正文；null 按空串
   * @param now      时间戳
   * @return 已就地更新的文章实体
   */
  public ArticleEntity applyMarkdown(ArticleEntity article, String markdown, LocalDateTime now) {
    return contentSave.applyMarkdown(article, markdown, now);
  }

  /**
   * 插入一条内容版本并回写 article.currentVersionNo。
   *
   * @param article 当前文章（读 content/hash）
   * @param source  版本来源码
   * @param reason  变更原因说明
   * @param now     创建时间
   * @return 新建版本实体
   */
  public ArticleVersionEntity insertContentVersion(
      ArticleEntity article, String source, String reason, LocalDateTime now) {
    return contentSave.insertContentVersion(article, source, reason, now);
  }

  /**
   * 查找文章当前版本号对应的版本行。
   *
   * @param article 文章实体
   * @return 版本实体，或 null
   */
  public ArticleVersionEntity findCurrentVersion(ArticleEntity article) {
    return versions.findCurrentVersion(article);
  }

  /**
   * 确保当前版本行存在；缺失则按正文写入 content_documents baseline。
   *
   * @param article 文章实体
   * @param now     时间戳
   * @return 已有或新建的当前版本行（id 为 content_document_versions.id）
   */
  public ArticleVersionEntity ensureCurrentVersionRow(ArticleEntity article, LocalDateTime now) {
    return versions.ensureCurrentVersionRow(article, now);
  }

  /**
   * 夹具修复：替换箭头与注释标记（仅本地 fixture / IT 路径使用）。
   *
   * @param content 原文
   * @return 替换后正文；输入 null 返回空串
   */
  public String applyFixtureRepairs(String content) {
    return repairs.applyFixtureRepairs(content);
  }

  /**
   * 从审核 Map 中取出 score 字段。
   *
   * @param audit 审核结果 Map
   * @return 分数字段；缺失或非法时 null
   */
  public Integer scoreOf(Map<String, Object> audit) {
    return repairs.scoreOf(audit);
  }
}
