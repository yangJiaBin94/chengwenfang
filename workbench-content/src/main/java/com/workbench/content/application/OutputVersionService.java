package com.workbench.content.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.common.BusinessException;
import com.workbench.common.ErrorCodes;
import cn.hutool.core.util.StrUtil;
import com.workbench.content.domain.entity.ArticleEntity;
import com.workbench.content.domain.entity.ArticleVersionEntity;
import com.workbench.content.domain.entity.WorkOutputVersionEntity;
import com.workbench.content.facade.dto.view.ArticleVersionAuditView;
import com.workbench.content.facade.dto.view.ArticleVersionView;
import com.workbench.content.facade.dto.view.ArticleView;
import com.workbench.content.infrastructure.mapper.WorkOutputVersionMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 产出版本：列表、恢复与当前版本行保障。
 */
@Service
public class OutputVersionService {
  private final WorkOutputVersionMapper workOutputVersionMapper;
  private final WorkContentStoreImpl workContents;
  private final ContentEntityUtil support;
  private final ThemeVisibilityGuard visibility;
  private final OutputContentSaveService contentSave;

  /**
   * @param workOutputVersionMapper 产出版本 Mapper
   * @param workContents            统一文档存储
   * @param support                 实体协作
   * @param visibility              主题可见性校验
   * @param contentSave             正文保存（恢复时写版本与视图）
   */
  public OutputVersionService(
      WorkOutputVersionMapper workOutputVersionMapper,
      WorkContentStoreImpl workContents,
      ContentEntityUtil support,
      ThemeVisibilityGuard visibility,
      OutputContentSaveService contentSave) {
    this.workOutputVersionMapper = workOutputVersionMapper;
    this.workContents = workContents;
    this.support = support;
    this.visibility = visibility;
    this.contentSave = contentSave;
  }

  /**
   * 列出文章历史版本。
   *
   * @param articleId  文章 id
   * @param documentId 统一文档 id；空则按文章解析
   * @return 版本视图列表
   */
  public List<ArticleVersionView> listArticleVersions(String articleId, String documentId) {
    visibility.assertThemeActiveForArticleId(articleId);
    String outputId = StrUtil.blankToDefault(documentId, articleId);
    return workOutputVersionMapper.selectList(new LambdaQueryWrapper<WorkOutputVersionEntity>()
            .eq(WorkOutputVersionEntity::getOutputId, outputId)
            .orderByDesc(WorkOutputVersionEntity::getVersionNo))
        .stream()
        .map(version -> workOutputVersionView(version, articleId))
        .toList();
  }

  /**
   * 列出文章历史版本（自动解析 documentId）。
   *
   * @param articleId 文章 id
   * @return 版本视图列表
   */
  public List<ArticleVersionView> listArticleVersions(String articleId) {
    return listArticleVersions(articleId, null);
  }

  /**
   * 将文章内容回滚到指定版本，并新增一条 restore 来源版本。
   *
   * @param articleId  文章 id
   * @param versionId  目标历史版本 id
   * @param documentId 统一文档 id；空则按文章解析
   * @return 恢复后的文章视图
   * @throws BusinessException 文章或版本不存在、或版本不属于该文章时
   */
  @Transactional
  public ArticleView restoreArticleVersion(String articleId, String versionId, String documentId) {
    visibility.assertThemeActiveForArticleId(articleId);
    ArticleEntity article = support.requireArticle(articleId);
    String resolvedOutputId = StrUtil.blankToDefault(documentId, articleId);
    WorkOutputVersionEntity version = workOutputVersionMapper.selectById(versionId);
    if (version == null || !resolvedOutputId.equals(version.getOutputId())) {
      throw new BusinessException(ErrorCodes.VERSION_NOT_FOUND, "文章版本不存在", 404);
    }
    Integer sourceVersionNo = version.getVersionNo();
    String content = workContents.readVersion(resolvedOutputId, sourceVersionNo);
    LocalDateTime now = LocalDateTime.now();
    article.setContent(content);
    article.setContentHash(ContentEntityUtil.sha256(content));
    article.setWordCount(content.length());
    article.setVersionToken((article.getVersionToken() == null ? 0 : article.getVersionToken()) + 1);
    article.setStatus("ready");
    article.setUpdatedAt(now);
    contentSave.insertContentVersion(article, "restore", "Restored from version " + sourceVersionNo, now);
    return contentSave.toArticleViewPreferringDocuments(article);
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
    return restoreArticleVersion(articleId, versionId, null);
  }

  /**
   * 查找文章当前版本号对应的版本行。
   *
   * @param article 文章实体
   * @return 版本实体，或 null
   */
  public ArticleVersionEntity findCurrentVersion(ArticleEntity article) {
    Integer versionNo = article.getCurrentVersionNo() == null ? 1 : article.getCurrentVersionNo();
    WorkOutputVersionEntity version = findWorkOutputVersion(article.getId(), versionNo);
    if (version == null) {
      return null;
    }
    return toLegacyVersionShape(version, article.getId());
  }

  /**
   * 确保当前版本行存在；缺失则按正文写入 content_documents baseline。
   *
   * @param article 文章实体
   * @param now     时间戳
   * @return 已有或新建的当前版本行（id 为 content_document_versions.id）
   */
  public ArticleVersionEntity ensureCurrentVersionRow(ArticleEntity article, LocalDateTime now) {
    // 壳 current_version_no 已停写：先从文档表投影再定位当前版本行
    support.overlayVersionIdentityFromDocuments(article);
    ArticleVersionEntity existing = findCurrentVersion(article);
    if (existing != null) {
      return existing;
    }
    if (article.getCurrentVersionNo() == null) {
      article.setCurrentVersionNo(1);
    }
    contentSave.overlayBodyFromDocuments(article);
    contentSave.mirrorCourseArticle(
        article,
        article.getCourseId(),
        article.getCurrentVersionNo(),
        "baseline",
        "Current article snapshot");
    WorkOutputVersionEntity version =
        findWorkOutputVersion(article.getId(), article.getCurrentVersionNo());
    if (version == null) {
      throw new BusinessException(
          ErrorCodes.VERSION_NOT_FOUND,
          "无法创建当前文档版本快照",
          500);
    }
    return toLegacyVersionShape(version, article.getId());
  }

  /**
   * 产出版本转前端视图。
   *
   * @param version   版本行
   * @param articleId 文章 id
   * @return 版本视图
   */
  private ArticleVersionView workOutputVersionView(
      WorkOutputVersionEntity version, String articleId) {
    String body = workContents.readVersion(version.getOutputId(), version.getVersionNo());
    return ArticleVersionView.builder()
        .id(version.getId())
        .articleId(articleId)
        .versionNo(version.getVersionNo())
        .content(body)
        .contentHash(version.getContentHash())
        .source(version.getSource())
        .reason(version.getReason() == null ? "" : version.getReason())
        .workflowRunId(version.getWorkflowRunId())
        .audit(ArticleVersionAuditView.builder().score(0).passed(0).warnings(0).failed(0).build())
        .createdAt(version.getCreatedAt() == null ? null : version.getCreatedAt().toString())
        .build();
  }

  /**
   * 版本行投影为旧 ArticleVersionEntity 形状（供审核等仍依赖该类型的调用方）。
   *
   * @param version   版本行
   * @param articleId 文章 id
   * @return 投影实体
   */
  private ArticleVersionEntity toLegacyVersionShape(
      WorkOutputVersionEntity version, String articleId) {
    ArticleVersionEntity legacy = new ArticleVersionEntity();
    legacy.setId(version.getId());
    legacy.setArticleId(articleId);
    legacy.setVersionNo(version.getVersionNo());
    legacy.setContent(workContents.readVersion(version.getOutputId(), version.getVersionNo()));
    legacy.setContentHash(version.getContentHash());
    legacy.setSource(version.getSource());
    legacy.setReason(version.getReason());
    legacy.setWorkflowRunId(version.getWorkflowRunId());
    legacy.setCreatedAt(version.getCreatedAt());
    return legacy;
  }

  /**
   * 按版本号查找产出版本行。
   *
   * @param outputId  产出 id
   * @param versionNo 版本号
   * @return 版本行；缺失为 null
   */
  WorkOutputVersionEntity findWorkOutputVersion(String outputId, int versionNo) {
    if (StrUtil.isBlank(outputId) || versionNo <= 0) {
      return null;
    }
    return workOutputVersionMapper.selectOne(new LambdaQueryWrapper<WorkOutputVersionEntity>()
        .eq(WorkOutputVersionEntity::getOutputId, outputId)
        .eq(WorkOutputVersionEntity::getVersionNo, versionNo)
        .last("LIMIT 1"));
  }
}
