package com.chengwenfang.content.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.content.domain.entity.ArticleEntity;
import com.chengwenfang.content.domain.entity.ArticleVersionEntity;
import com.chengwenfang.content.domain.entity.HumanReviewEntity;
import com.chengwenfang.content.facade.dto.request.HumanReviewRequest;
import com.chengwenfang.content.facade.dto.view.CourseShellView;
import com.chengwenfang.content.facade.dto.view.HumanReviewImportEntry;
import com.chengwenfang.content.facade.dto.view.HumanReviewView;
import com.chengwenfang.content.facade.dto.view.ImportHumanReviewsView;
import com.chengwenfang.content.facade.enums.HumanReviewStatus;
import com.chengwenfang.content.infrastructure.mapper.HumanReviewMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 人工审核读写与批量导入。
 */
@Service
public class HumanReviewService {
  private final HumanReviewMapper humanReviewMapper;
  private final ArticleVersionService articleVersions;
  private final CourseShellService shell;
  private final ContentViewMapper views;
  private final ThemeVisibilityGuard visibility;
  /** 产出身份解析。 */
  private final WorkContentStoreImpl workContents;
  /** 实体工具：version sole-read。 */
  private final ContentEntityUtil support;

  public HumanReviewService(
      HumanReviewMapper humanReviewMapper,
      ArticleVersionService articleVersions,
      CourseShellService shell,
      ContentViewMapper views,
      ThemeVisibilityGuard visibility,
      WorkContentStoreImpl workContents,
      ContentEntityUtil support) {
    this.humanReviewMapper = humanReviewMapper;
    this.articleVersions = articleVersions;
    this.shell = shell;
    this.views = views;
    this.visibility = visibility;
    this.workContents = workContents;
    this.support = support;
  }

  /**
   * 读取最新人工审核。
   *
   * @param articleId  文章 id
   * @param documentId 统一文档/产出 id；空则按 articleId 解析
   * @return 人工审核视图
   * @throws BusinessException 无审核记录时 404
   */
  public HumanReviewView getHumanReview(String articleId, String documentId) {
    visibility.assertThemeActiveForArticleId(articleId);
    HumanReviewEntity review = findLatestReview(articleId, documentId);
    if (review == null) {
      throw new BusinessException("human_review_not_found", "人工审核记录不存在", 404);
    }
    Integer versionNo = null;
    ArticleEntity article = support.findArticleOrNull(articleId);
    if (article != null) {
      support.overlayVersionIdentityFromDocuments(article);
      versionNo = article.getCurrentVersionNo();
    }
    return views.humanReviewView(review, versionNo, articleId);
  }

  /**
   * 读取最新人工审核（自动解析 documentId）。
   *
   * @param articleId 文章 id
   * @return 人工审核视图
   */
  public HumanReviewView getHumanReview(String articleId) {
    return getHumanReview(articleId, null);
  }

  /**
   * 写入或更新人工审核（approved / rejected），并绑定当前文章版本。
   *
   * @param articleId 文章 id
   * @param body      审核请求；status 须为 approved 或 rejected
   * @return 写入后的审核视图
   * @throws BusinessException status 非法时 400
   */
  @Transactional
  public HumanReviewView putHumanReview(String articleId, HumanReviewRequest body) {
    visibility.assertThemeActiveForArticleId(articleId);
    LocalDateTime now = LocalDateTime.now();
    ArticleEntity article = support.requireArticle(articleId);
    support.overlayVersionIdentityFromDocuments(article);
    String explicitDocumentId = body == null ? null : body.getDocumentId();
    String outputId = StrUtil.blankToDefault(
        explicitDocumentId, resolveDocumentId(articleId, article));
    String outputVersionId = null;
    Integer versionNo = null;
    if (article != null) {
      ArticleVersionEntity current = articleVersions.ensureCurrentVersionRow(article, now);
      outputVersionId = current.getId();
      versionNo = current.getVersionNo();
    }
    String status = body == null || body.getStatus() == null ? "" : body.getStatus();
    if (!HumanReviewStatus.APPROVED.getValue().equals(status)
        && !HumanReviewStatus.REJECTED.getValue().equals(status)) {
      throw new BusinessException("invalid_review_status", "status 必须为 approved 或 rejected", 400);
    }
    String comment = body == null || body.getComment() == null ? "" : body.getComment();
    HumanReviewEntity review = findLatestReview(articleId, outputId);
    if (review == null) {
      review = new HumanReviewEntity();
      review.setId(IdsUtil.newId());
      review.setOutputId(outputId);
      review.setOutputVersionId(outputVersionId);
      review.setCreatedAt(now);
      review.setStatus(status);
      review.setComment(comment);
      review.setReviewedAt(now);
      humanReviewMapper.insert(review);
    } else {
      review.setStatus(status);
      review.setComment(comment);
      review.setReviewedAt(now);
      if (StrUtil.isNotBlank(outputId)) {
        review.setOutputId(outputId);
      }
      if (outputVersionId != null) {
        review.setOutputVersionId(outputVersionId);
      }
      humanReviewMapper.updateById(review);
    }
    return views.humanReviewView(review, versionNo, articleId);
  }

  /**
   * 解析审核应挂的产出 id（课程 = articles.id；否则走公众号轨解析）。
   *
   * @param articleId 文章 id
   * @param article   课程文章实体；热点时为 null
   * @return output_id
   */
  private String resolveDocumentId(String articleId, ArticleEntity article) {
    if (article != null) {
      // 课程文章 id 即产出 id
      return article.getId();
    }
    return workContents.resolvePublishingOutputId(articleId);
  }

  /**
   * 按 output_id 取最新审核行。
   *
   * @param articleId  文章 id（用于解析产出）
   * @param documentId 产出 id；可空
   * @return 最新审核实体；无则 null
   */
  private HumanReviewEntity findLatestReview(String articleId, String documentId) {
    String outputId = StrUtil.isNotBlank(documentId)
        ? documentId
        : resolveDocumentId(articleId, support.findArticleOrNull(articleId));
    if (StrUtil.isBlank(outputId)) {
      return null;
    }
    return humanReviewMapper.selectOne(new LambdaQueryWrapper<HumanReviewEntity>()
        .eq(HumanReviewEntity::getOutputId, outputId)
        .orderByDesc(HumanReviewEntity::getCreatedAt)
        .last("LIMIT 1"));
  }

  /**
   * 删除文章全部人工审核记录。
   *
   * @param articleId  文章 id
   * @param documentId 统一文档/产出 id；空则按 articleId 解析
   */
  @Transactional
  public void deleteHumanReview(String articleId, String documentId) {
    visibility.assertThemeActiveForArticleId(articleId);
    String outputId = StrUtil.isNotBlank(documentId)
        ? documentId
        : resolveDocumentId(articleId, support.findArticleOrNull(articleId));
    if (StrUtil.isBlank(outputId)) {
      return;
    }
    humanReviewMapper.delete(new LambdaQueryWrapper<HumanReviewEntity>()
        .eq(HumanReviewEntity::getOutputId, outputId));
  }

  /**
   * 删除文章全部人工审核记录（按 articleId）。
   *
   * @param articleId 文章 id
   */
  @Transactional
  public void deleteHumanReview(String articleId) {
    deleteHumanReview(articleId, null);
  }

  /**
   * 批量导入历史人工审核到对应课程的公众号文章。
   *
   * @param entries 导入条目（含 courseId、status、comment）
   * @return 导入统计与成功/跳过 key 列表
   */
  @Transactional
  public ImportHumanReviewsView importHumanReviews(List<HumanReviewImportEntry> entries) {
    List<String> migratedKeys = new ArrayList<>();
    List<String> skippedKeys = new ArrayList<>();
    int imported = 0;
    if (entries != null) {
      for (HumanReviewImportEntry entry : entries) {
        String courseId = entry == null ? null : entry.getCourseId();
        String key = "chengwenfang-review:" + (courseId == null ? "" : courseId);
        try {
          if (courseId == null || courseId.isBlank()) {
            skippedKeys.add(key);
            continue;
          }
          CourseShellView courseShell = shell.ensureCourseShell(courseId);
          String articleId = courseShell.getWechatArticleId();
          HumanReviewRequest reviewReq = HumanReviewRequest.builder()
              .status(entry.getStatus() == null ? HumanReviewStatus.APPROVED.getValue() : entry.getStatus())
              .comment(entry.getComment() == null ? "" : entry.getComment())
              .build();
          putHumanReview(articleId, reviewReq);
          migratedKeys.add(key);
          imported++;
        } catch (Exception ignored) {
          skippedKeys.add(key);
        }
      }
    }
    return ImportHumanReviewsView.builder()
        .imported(imported)
        .migratedKeys(migratedKeys)
        .skippedKeys(skippedKeys)
        .ok(true)
        .build();
  }
}
