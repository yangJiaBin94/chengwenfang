package com.chengwenfang.content.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.content.domain.entity.ArticleEntity;
import com.chengwenfang.content.domain.entity.ArticleLayoutEntity;
import com.chengwenfang.content.facade.dto.request.ArticleLayoutSaveRequest;
import com.chengwenfang.content.facade.dto.view.ArticleLayoutView;
import com.chengwenfang.content.infrastructure.mapper.ArticleLayoutMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 产出排版布局的获取与保存。
 */
@Service
public class ArticleLayoutService {
  private final ArticleLayoutMapper articleLayoutMapper;
  private final ContentEntityUtil support;
  private final ContentViewMapper views;
  private final ThemeVisibilityGuard visibility;
  /** 产出正文与身份解析。 */
  private final WorkContentStoreImpl workContents;

  public ArticleLayoutService(
      ArticleLayoutMapper articleLayoutMapper,
      ContentEntityUtil support,
      ContentViewMapper views,
      ThemeVisibilityGuard visibility,
      WorkContentStoreImpl workContents) {
    this.articleLayoutMapper = articleLayoutMapper;
    this.support = support;
    this.views = views;
    this.visibility = visibility;
    this.workContents = workContents;
  }

  /**
   * 获取排版配置，不存在则初始化空配置。
   *
   * @param articleId 文章 id
   * @return 布局视图
   */
  @Transactional
  public ArticleLayoutView getOrInitLayout(String articleId) {
    return getOrInitLayout(articleId, null);
  }

  /**
   * 获取排版配置，不存在则初始化空配置。
   *
   * @param articleId  文章 id
   * @param documentId 产出 id；空则服务端解析
   * @return 布局视图
   */
  @Transactional
  public ArticleLayoutView getOrInitLayout(String articleId, String documentId) {
    visibility.assertThemeActiveForArticleId(articleId);
    String outputId = requireOutputId(articleId, documentId);
    ArticleLayoutEntity layout = findLayout(outputId);
    LocalDateTime now = LocalDateTime.now();
    if (layout == null) {
      // 首次访问：按当前文章版本号建空布局
      ArticleEntity article = support.findArticleOrNull(articleId);
      if (article != null) {
        support.overlayVersionIdentityFromDocuments(article);
      }
      layout = new ArticleLayoutEntity();
      layout.setId(IdsUtil.newId());
      layout.setOutputId(outputId);
      layout.setOutputVersionNo(article == null || article.getCurrentVersionNo() == null
          ? 1
          : article.getCurrentVersionNo());
      layout.setLayoutThemeVersionId("");
      layout.setCreatedAt(now);
      layout.setUpdatedAt(now);
      articleLayoutMapper.insert(layout);
    }
    return views.layoutView(layout, articleId);
  }

  /**
   * 保存排版主题与选项。
   *
   * @param articleId 文章 id
   * @param body      保存请求；null 按空请求
   * @return 保存后的布局视图
   */
  @Transactional
  public ArticleLayoutView saveLayout(String articleId, ArticleLayoutSaveRequest body) {
    ArticleLayoutSaveRequest input = body == null ? new ArticleLayoutSaveRequest() : body;
    return saveLayout(articleId, input, input.getDocumentId());
  }

  /**
   * 保存排版主题与选项（options 恒不落库，视图仍返回空对象）。
   *
   * @param articleId  文章 id
   * @param body       保存请求；null 按空请求
   * @param documentId 产出 id；空则服务端解析或取 body.documentId
   * @return 保存后的布局视图
   */
  @Transactional
  public ArticleLayoutView saveLayout(String articleId, ArticleLayoutSaveRequest body, String documentId) {
    visibility.assertThemeActiveForArticleId(articleId);
    LocalDateTime now = LocalDateTime.now();
    ArticleLayoutSaveRequest input = body == null ? new ArticleLayoutSaveRequest() : body;
    String outputId = requireOutputId(
        articleId, StrUtil.blankToDefault(documentId, input.getDocumentId()));
    ArticleLayoutEntity layout = findLayout(outputId);
    ArticleEntity article = support.findArticleOrNull(articleId);
    if (article != null) {
      support.overlayVersionIdentityFromDocuments(article);
    }
    // 未指定版本号时跟当前文章版本
    int versionNo = input.getArticleVersionNo() != null
        ? input.getArticleVersionNo()
        : (article == null || article.getCurrentVersionNo() == null ? 1 : article.getCurrentVersionNo());
    String themeVersionId = input.getThemeVersionId() == null ? "" : input.getThemeVersionId();
    if (layout == null) {
      layout = new ArticleLayoutEntity();
      layout.setId(IdsUtil.newId());
      layout.setOutputId(outputId);
      layout.setCreatedAt(now);
      layout.setOutputVersionNo(versionNo);
      layout.setLayoutThemeVersionId(themeVersionId);
      layout.setUpdatedAt(now);
      articleLayoutMapper.insert(layout);
    } else {
      layout.setOutputVersionNo(versionNo);
      layout.setLayoutThemeVersionId(themeVersionId);
      layout.setUpdatedAt(now);
      articleLayoutMapper.updateById(layout);
    }
    return views.layoutView(layout, articleId);
  }

  /**
   * 解析并校验产出 ID。
   *
   * @param articleId  文章 id
   * @param documentId 请求中的产出 id；可空
   * @return 非空产出 ID
   */
  private String requireOutputId(String articleId, String documentId) {
    String outputId = StrUtil.blankToDefault(
        documentId, workContents.resolvePublishingOutputId(articleId));
    if (StrUtil.isBlank(outputId)) {
      throw new BusinessException("output_not_found", "文章尚未关联公众号产出，无法排版", 404);
    }
    return outputId.trim();
  }

  /**
   * 按产出 ID 查找布局。
   *
   * @param outputId 产出 id
   * @return 布局实体；无则 null
   */
  private ArticleLayoutEntity findLayout(String outputId) {
    return articleLayoutMapper.selectOne(new LambdaQueryWrapper<ArticleLayoutEntity>()
        .eq(ArticleLayoutEntity::getOutputId, outputId)
        .last("LIMIT 1"));
  }
}
