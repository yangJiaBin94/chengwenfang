package com.chengwenfang.content.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.content.domain.entity.ArticleEntity;
import com.chengwenfang.content.domain.entity.CourseEntity;
import com.chengwenfang.content.domain.entity.CourseOutputSourceEntity;
import com.chengwenfang.content.domain.entity.MediaAssetEntity;
import com.chengwenfang.content.facade.dto.view.ContentModeView;
import com.chengwenfang.content.facade.dto.view.ContentThemeView;
import com.chengwenfang.content.facade.dto.view.CourseContentView;
import com.chengwenfang.content.facade.dto.view.CourseMediaView;
import com.chengwenfang.content.facade.dto.view.CourseView;
import com.chengwenfang.content.facade.dto.view.ChengwenfangDataView;
import com.chengwenfang.content.facade.dto.view.ChengwenfangMetricsView;
import com.chengwenfang.content.facade.dto.view.ChengwenfangWorkspaceView;
import com.chengwenfang.content.facade.enums.ArticleKind;
import com.chengwenfang.content.facade.enums.HumanReviewStatus;
import com.chengwenfang.content.infrastructure.mapper.CourseOutputSourceMapper;
import com.chengwenfang.content.infrastructure.mapper.MediaAssetMapper;
import com.chengwenfang.file.facade.FileFacade;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 课程内容视图：成文坊首页、正文、媒资读写。
 */
@Service
@RequiredArgsConstructor
public class CourseContentViewService {
  /** 课程作品存储。 */
  private final CourseStore courseStore;
  /** 媒资表。 */
  private final MediaAssetMapper mediaAssetMapper;
  /** 课程产出源路径。 */
  private final CourseOutputSourceMapper courseOutputSourceMapper;
  /** 产出正文。 */
  private final WorkContentStoreImpl workContents;
  /** 文件门面。 */
  private final FileFacade files;
  /** 实体工具。 */
  private final ContentEntityUtil support;
  /** 视图映射。 */
  private final ContentViewMapper views;
  /** 主题可见性。 */
  private final ThemeVisibilityGuard visibility;
  /** 课次列表（成文坊聚合）。 */
  private final CourseLessonService lessons;

  /**
   * 组装成文坊首页数据：主题、课程列表与进度指标。
   *
   * @param themeId 主题 id/code；空则用默认课程体系主题
   * @return 含 workspace、courses、metrics 的聚合视图
   */
  public ChengwenfangDataView chengwenfangData(String themeId) {
    // 解析主题；未知 id 回退到课程体系默认主题
    ContentThemeView theme = views.resolveTheme(themeId);
    if (theme.getDeletedAt() != null) {
      visibility.assertThemeActiveById(theme.getId());
    }
    String resolvedThemeId = theme.getId();
    // 热点主题无课程列表；课程体系主题按 theme_id 过滤
    boolean isHotNews = ContentViewMapper.HOT_NEWS_THEME_ID.equals(resolvedThemeId)
        || "ai_frontier".equals(theme.getCode());
    List<CourseView> courses = isHotNews ? List.of() : lessons.listCourses(resolvedThemeId);
    // 统计已人工审核的课程数
    int reviewed = 0;
    for (CourseView course : courses) {
      String status = course.getStatus() == null ? "" : course.getStatus();
      if ("人工已审核".equals(status)
          || "reviewed".equalsIgnoreCase(status)
          || HumanReviewStatus.APPROVED.getValue().equalsIgnoreCase(status)) {
        reviewed++;
      }
    }

    ChengwenfangWorkspaceView workspace = ChengwenfangWorkspaceView.builder()
        .name("成文坊")
        .moduleName("自学课程及自媒体内容生成")
        .topicName(theme.getName())
        .topicSlug(String.valueOf(theme.getCode()).replace('_', '-'))
        .modeId(theme.getModeId())
        .modeName(theme.getModeName())
        .modeCode(theme.getModeCode())
        .themeId(theme.getId())
        .themeName(theme.getName())
        .themeCode(theme.getCode())
        .build();

    ChengwenfangMetricsView metrics = ChengwenfangMetricsView.builder()
        .courseCount(courses.size())
        .reviewedCount(reviewed)
        .pendingCount(Math.max(0, courses.size() - reviewed))
        .imageCount(0)
        .build();

    return ChengwenfangDataView.builder()
        .workspace(workspace)
        .generatedAt(LocalDateTime.now().toString())
        .courses(courses)
        .metrics(metrics)
        .build();
  }

  /**
   * 列出可用教学模式（课程体系 / 热点资讯）。
   *
   * @return 模式视图列表（目前固定两项，含各自主题）
   */
  public List<ContentModeView> listModes() {
    return views.listModes();
  }

  /**
   * 获取课程正文与媒资：原稿、公众号稿，并将历史 public 路径重写为可访问 URL。
   *
   * @param courseId 课程 id
   * @return 正文与媒资视图；课程缺失时正文为空串
   */
  public CourseContentView getCourseContent(String courseId) {
    CourseEntity course = courseStore.selectById(courseId);
    visibility.assertThemeActiveForCourse(course);
    // 文档指针 sole-read content_documents（owner_id=work_id + kind）
    String draftId = lessons.resolveCourseArticleId(course, ArticleKind.COURSE_DRAFT);
    String articleId = lessons.resolveCourseArticleId(course, ArticleKind.WECHAT_ARTICLE);
    ArticleEntity draft = draftId == null ? null : support.findArticleOrNull(draftId);
    ArticleEntity article = articleId == null ? null : support.findArticleOrNull(articleId);
    List<CourseMediaView> images = listCourseMedia(courseId);

    // 正文与路径元数据 sole-read：仅 content_documents（壳行缺失时仍可按 id+kind 读正文）
    return CourseContentView.builder()
        .courseId(courseId)
        .draftArticleId(draftId)
        .articleArticleId(articleId)
        .draftDocumentId(resolveCourseDocumentId(draftId, ArticleKind.COURSE_DRAFT.getValue()))
        .articleDocumentId(resolveCourseDocumentId(articleId, ArticleKind.WECHAT_ARTICLE.getValue()))
        .draftFileId(resolveCourseSourceFileId(draft, draftId, ArticleKind.COURSE_DRAFT.getValue()))
        .articleFileId(resolveCourseSourceFileId(article, articleId, ArticleKind.WECHAT_ARTICLE.getValue()))
        .draftFilePath(resolveCourseFilePath(draft, draftId, ArticleKind.COURSE_DRAFT.getValue()))
        .articleFilePath(resolveCourseFilePath(article, articleId, ArticleKind.WECHAT_ARTICLE.getValue()))
        .draft(ContentEntityUtil.rewriteContentMediaUrls(
            resolveArticleBody(draft, draftId, ArticleKind.COURSE_DRAFT.getValue()), images))
        .article(ContentEntityUtil.rewriteContentMediaUrls(
            resolveArticleBody(article, articleId, ArticleKind.WECHAT_ARTICLE.getValue()), images))
        .images(images)
        .build();
  }

  /**
   * 解析课程轨统一文档 id（与壳 articleId 同值；空指针则 null）。
   *
   * @param articleId 壳文章 id
   * @param kind      文档 kind
   * @return documentId；articleId 空则为 null
   */
  private String resolveCourseDocumentId(String articleId, String kind) {
    return StrUtil.isBlank(articleId) ? null : articleId;
  }

  /**
   * 解析课程同步相对路径。
   *
   * @param article   文章实体；可为 null
   * @param articleId 回退用产出 id
   * @param kind      文档 kind（未用）
   * @return 相对路径；均可空
   */
  private String resolveCourseFilePath(ArticleEntity article, String articleId, String kind) {
    if (article != null && StrUtil.isNotBlank(article.getFilePath())) {
      return article.getFilePath();
    }
    String id = article != null && StrUtil.isNotBlank(article.getId()) ? article.getId() : articleId;
    CourseOutputSourceEntity source = StrUtil.isBlank(id) ? null : courseOutputSourceMapper.selectById(id);
    return source == null ? null : source.getSyncRelativePath();
  }

  /**
   * 解析课程源文件 id。
   *
   * @param article   文章实体；可为 null
   * @param articleId 回退用产出 id
   * @param kind      文档 kind（未用）
   * @return stored_files id；均可空
   */
  private String resolveCourseSourceFileId(ArticleEntity article, String articleId, String kind) {
    if (article != null && StrUtil.isNotBlank(article.getSourceFileId())) {
      return article.getSourceFileId();
    }
    String id = article != null && StrUtil.isNotBlank(article.getId()) ? article.getId() : articleId;
    CourseOutputSourceEntity source = StrUtil.isBlank(id) ? null : courseOutputSourceMapper.selectById(id);
    return source == null ? null : source.getSourceFileId();
  }

  /**
   * 读取课程产出正文。
   *
   * @param article   文章实体；可为 null
   * @param articleId 回退用产出 id
   * @param kind      文档 kind（未用）
   * @return 正文 Markdown
   */
  private String resolveArticleBody(ArticleEntity article, String articleId, String kind) {
    String id = article != null && StrUtil.isNotBlank(article.getId()) ? article.getId() : articleId;
    if (StrUtil.isBlank(id)) {
      return "";
    }
    return StrUtil.nullToEmpty(workContents.readCurrent(id));
  }

  /**
   * 列出课程媒资（配图等），按序号与别名排序。
   *
   * @param courseId 课程 id；空则返回空列表
   * @return 媒资视图列表
   */
  public List<CourseMediaView> listCourseMedia(String courseId) {
    if (courseId == null || courseId.isBlank()) {
      return List.of();
    }
    visibility.assertThemeActiveForCourseId(courseId);
    return mediaAssetMapper.selectList(new LambdaQueryWrapper<MediaAssetEntity>()
            .eq(MediaAssetEntity::getWorkId, courseId)
            .orderByAsc(MediaAssetEntity::getSequenceNo)
            .orderByAsc(MediaAssetEntity::getAltText))
        .stream()
        .map(views::mediaAssetView)
        .toList();
  }

  /**
   * 获取单个媒资元数据。
   *
   * @param mediaId 媒资 id
   * @return 媒资视图
   * @throws BusinessException 媒资不存在时 404
   */
  public CourseMediaView getMediaAsset(String mediaId) {
    visibility.assertThemeActiveForMediaId(mediaId);
    // 校验存在后组装含 URL 的媒资视图
    return views.mediaAssetView(support.requireMedia(mediaId));
  }

  /**
   * 解析媒资本地文件路径。
   *
   * @param mediaId 媒资 id
   * @return 本地磁盘路径
   * @throws BusinessException 媒资或文件不存在时
   */
  public Path getMediaFile(String mediaId) {
    visibility.assertThemeActiveForMediaId(mediaId);
    // 经 fileId 解析本地磁盘路径
    MediaAssetEntity media = support.requireMedia(mediaId);
    return files.getLocalPath(media.getFileId());
  }

  /**
   * 读取媒资二进制内容。
   *
   * @param mediaId 媒资 id
   * @return 文件字节
   * @throws BusinessException 媒资或文件不存在时
   */
  public byte[] getMediaBytes(String mediaId) {
    visibility.assertThemeActiveForMediaId(mediaId);
    MediaAssetEntity media = support.requireMedia(mediaId);
    return files.getBytes(media.getFileId());
  }
}
