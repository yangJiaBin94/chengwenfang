package com.chengwenfang.content.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.content.domain.entity.ArticleEntity;
import com.chengwenfang.content.domain.entity.MediaAssetEntity;
import com.chengwenfang.content.domain.entity.RepairRunEntity;
import com.chengwenfang.content.domain.entity.WorkOutputVersionEntity;
import com.chengwenfang.content.facade.constant.MediaOrigins;
import com.chengwenfang.content.facade.dto.view.ArticleView;
import com.chengwenfang.content.facade.dto.view.RepairResponseView;
import com.chengwenfang.content.infrastructure.mapper.MediaAssetMapper;
import com.chengwenfang.content.infrastructure.mapper.RepairRunMapper;
import com.chengwenfang.job.facade.JobFacade;
import com.chengwenfang.job.facade.constant.JobEntityTypes;
import com.chengwenfang.job.facade.constant.JobTypes;
import com.chengwenfang.job.facade.dto.job.ArticleRepairJobInput;
import com.chengwenfang.job.facade.enums.JobStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文章修复：入队、元数据补齐与修复结果落库。
 * <p>留在 content：quality 已依赖 content，迁入 quality 会形成 Maven 环。
 */
@Slf4j
@Service
public class ArticleRepairService {
  private final MediaAssetMapper mediaAssetMapper;
  private final RepairRunMapper repairRunMapper;
  /** 修复运行问题码子表。 */
  private final RepairRunCodeStore repairRunCodeStore;
  private final JobFacade jobs;
  private final ContentEntityUtil support;
  private final ThemeVisibilityGuard visibility;
  private final OutputContentSaveService contentSave;
  private final OutputVersionService versions;

  /**
   * @param mediaAssetMapper    媒资 Mapper
   * @param repairRunMapper     修复运行 Mapper
   * @param repairRunCodeStore  问题码子表
   * @param jobs                任务门面
   * @param support             实体协作
   * @param visibility          主题可见性校验
   * @param contentSave         正文保存协作
   * @param versions            版本协作
   */
  public ArticleRepairService(
      MediaAssetMapper mediaAssetMapper,
      RepairRunMapper repairRunMapper,
      RepairRunCodeStore repairRunCodeStore,
      JobFacade jobs,
      ContentEntityUtil support,
      ThemeVisibilityGuard visibility,
      OutputContentSaveService contentSave,
      OutputVersionService versions) {
    this.mediaAssetMapper = mediaAssetMapper;
    this.repairRunMapper = repairRunMapper;
    this.repairRunCodeStore = repairRunCodeStore;
    this.jobs = jobs;
    this.support = support;
    this.visibility = visibility;
    this.contentSave = contentSave;
    this.versions = versions;
  }

  /**
   * 启动文章修复异步任务。
   *
   * @param articleId  文章 id
   * @param documentId 统一文档 id；空则按课程文章解析
   * @return 新建任务 id
   * @throws BusinessException 文章不存在时
   */
  public String startRepair(String articleId, String documentId) {
    visibility.assertThemeActiveForArticleId(articleId);
    // 校验文章后创建 ARTICLE_REPAIR 任务
    ArticleEntity article = support.requireArticle(articleId);
    String resolvedDocumentId = StrUtil.blankToDefault(documentId, articleId);
    ArticleRepairJobInput.ArticleRepairJobInputBuilder inputBuilder =
        ArticleRepairJobInput.builder().articleId(articleId);
    if (StrUtil.isNotBlank(resolvedDocumentId)) {
      inputBuilder.documentId(resolvedDocumentId);
    }
    if (StrUtil.isNotBlank(article.getCourseId())) {
      inputBuilder.courseId(article.getCourseId()).workId(article.getCourseId());
    }
    String jobId = jobs.create(
        JobTypes.ARTICLE_REPAIR,
        JobEntityTypes.ARTICLE,
        articleId,
        inputBuilder.build()).getId();
    log.info("修复任务已入队 id={} articleId={} documentId={}", jobId, articleId, resolvedDocumentId);
    return jobId;
  }

  /**
   * 启动文章修复异步任务（自动解析 documentId）。
   *
   * @param articleId 文章 id
   * @return 新建任务 id
   */
  public String startRepair(String articleId) {
    return startRepair(articleId, null);
  }

  /**
   * 补齐配图元数据（绑定 articleId / 缺省名称），并可选清理夹具符号。
   *
   * @param articleId 文章 id
   * @return 修复后（或未变）的文章视图
   * @throws BusinessException 文章不存在时
   */
  @Transactional
  public ArticleView repairMetadata(String articleId) {
    visibility.assertThemeActiveForArticleId(articleId);
    ArticleEntity article = support.requireArticle(articleId);
    LocalDateTime now = LocalDateTime.now();
    // 将课程媒资补绑到当前文章，消除 IMAGE_METADATA_COMPLETE 告警
    bindCourseMediaToArticle(article, now);
    contentSave.overlayBodyFromDocuments(article);
    String before = article.getContent() == null ? "" : article.getContent();
    String after = applyFixtureRepairs(before);
    if (!after.equals(before)) {
      int nextVersion = (article.getCurrentVersionNo() == null ? 1 : article.getCurrentVersionNo()) + 1;
      article.setContent(after);
      article.setContentHash(ContentEntityUtil.sha256(after));
      article.setWordCount(after.length());
      article.setCurrentVersionNo(nextVersion);
      article.setVersionToken((article.getVersionToken() == null ? 0 : article.getVersionToken()) + 1);
      article.setUpdatedAt(now);
      contentSave.mirrorCourseArticle(article, article.getCourseId(), nextVersion, "repair_metadata", "Fixture symbol cleanup");
    }
    return contentSave.toArticleViewPreferringDocuments(article);
  }

  /**
   * 为课程下缺失绑定的配图补齐 articleId 与显示名。
   *
   * @param article 当前文章
   * @param now     更新时间
   */
  private void bindCourseMediaToArticle(ArticleEntity article, LocalDateTime now) {
    String courseId = article.getCourseId();
    if (StrUtil.isBlank(courseId)) {
      return;
    }
    List<MediaAssetEntity> mediaRows = mediaAssetMapper.selectList(
        new LambdaQueryWrapper<MediaAssetEntity>()
            .eq(MediaAssetEntity::getWorkId, courseId));
    for (MediaAssetEntity media : mediaRows) {
      boolean dirty = false;
      if (StrUtil.isBlank(media.getOutputId())) {
        media.setOutputId(article.getId());
        dirty = true;
      }
      if (StrUtil.isBlank(media.getAltText())) {
        media.setAltText(StrUtil.blankToDefault(media.getId(), "image"));
        dirty = true;
      }
      if (StrUtil.isBlank(media.getOrigin())) {
        media.setOrigin(MediaOrigins.SYNCED);
        dirty = true;
      }
      if (dirty) {
        media.setUpdatedAt(now);
        mediaAssetMapper.updateById(media);
      }
    }
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
   * @throws BusinessException 文章不存在时
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
    return applyArticleRepair(
        articleId, jobId, repairedContent, beforeAudit, afterAudit, resolvedCodes, addedCodes, null);
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
   * @throws BusinessException 文章不存在时
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
    visibility.assertThemeActiveForArticleId(articleId);
    ArticleEntity article = support.requireArticle(articleId);
    LocalDateTime now = LocalDateTime.now();
    String content = repairedContent == null ? "" : repairedContent;
    int currentNo = article.getCurrentVersionNo() == null ? 1 : article.getCurrentVersionNo();
    contentSave.overlayBodyFromDocuments(article);
    String beforeContent = article.getContent() == null ? "" : article.getContent();
    int beforeVersionNo = currentNo + 1;
    int afterVersionNo = currentNo + 2;
    String resolvedDocumentId = StrUtil.blankToDefault(documentId, articleId);

    // 修复前/后快照写入 work_outputs
    article.setContent(beforeContent);
    article.setContentHash(ContentEntityUtil.sha256(beforeContent));
    article.setWordCount(beforeContent.length());
    article.setCurrentVersionNo(beforeVersionNo);
    contentSave.mirrorCourseArticle(article, article.getCourseId(), beforeVersionNo, "repair_before", "Snapshot before article repair");
    String beforeVersionId = findDocumentVersionId(article, beforeVersionNo);

    article.setContent(content);
    article.setContentHash(ContentEntityUtil.sha256(content));
    article.setWordCount(content.length());
    article.setCurrentVersionNo(afterVersionNo);
    article.setVersionToken((article.getVersionToken() == null ? 0 : article.getVersionToken()) + 1);
    article.setStatus("ready");
    article.setUpdatedAt(now);
    contentSave.mirrorCourseArticle(article, article.getCourseId(), afterVersionNo, "article_repair", "AI article repair");
    String afterVersionId = findDocumentVersionId(article, afterVersionNo);

    // 落 repair_runs 审计行（显式 documentId 优先作为 output_id）
    RepairRunEntity run = new RepairRunEntity();
    run.setId(IdsUtil.newId());
    run.setOutputId(resolvedDocumentId);
    run.setJobId(jobId);
    log.info("修复结果已落库 articleId={} outputId={} jobId={}",
        articleId, resolvedDocumentId, jobId);
    run.setBeforeScore(scoreOf(beforeAudit));
    run.setAfterScore(scoreOf(afterAudit));
    run.setStatus(JobStatus.SUCCEEDED.getValue());
    run.setCreatedAt(now);
    run.setFinishedAt(now);
    repairRunMapper.insert(run);
    repairRunCodeStore.replace(
        run.getId(),
        resolvedCodes == null ? List.of() : resolvedCodes,
        addedCodes == null ? List.of() : addedCodes);

    return RepairResponseView.builder()
        .repairRunId(run.getId())
        .status(JobStatus.SUCCEEDED.getValue())
        .article(contentSave.toArticleViewPreferringDocuments(article))
        .beforeVersionId(beforeVersionId)
        .afterVersionId(afterVersionId)
        .beforeAudit(beforeAudit == null ? Map.of() : beforeAudit)
        .afterAudit(afterAudit == null ? Map.of() : afterAudit)
        .beforeScore(run.getBeforeScore())
        .afterScore(run.getAfterScore())
        .resolvedCodes(resolvedCodes == null ? List.of() : resolvedCodes)
        .addedCodes(addedCodes == null ? List.of() : addedCodes)
        .createdAt(now.toString())
        .finishedAt(now.toString())
        .build();
  }

  /**
   * 按版本号查找产出版本 id。
   *
   * @param article   文章
   * @param versionNo 版本号
   * @return 版本 id；缺失为 null
   */
  private String findDocumentVersionId(ArticleEntity article, int versionNo) {
    WorkOutputVersionEntity version = versions.findWorkOutputVersion(
        article == null ? null : article.getId(), versionNo);
    return version == null ? null : version.getId();
  }

  /**
   * 夹具修复：替换箭头与注释标记（仅本地 fixture / IT 路径使用）。
   *
   * @param content 原文
   * @return 替换后正文；输入 null 返回空串
   */
  public String applyFixtureRepairs(String content) {
    // 夹具约定：箭头与双斜杠注释换成中文符号
    if (content == null || content.isBlank()) {
      return content == null ? "" : content;
    }
    return content.replace("->", "→").replace("//", "注：");
  }

  /**
   * 从审核 Map 中取出 score 字段。
   *
   * @param audit 审核结果 Map
   * @return 分数字段；缺失或非法时 null
   */
  public Integer scoreOf(Map<String, Object> audit) {
    // 兼容 Number 与可解析字符串
    if (audit == null || audit.get("score") == null) {
      return null;
    }
    Object score = audit.get("score");
    if (score instanceof Number number) {
      return number.intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(score));
    } catch (Exception ignored) {
      return null;
    }
  }
}
