package com.workbench.content.infrastructure.assembler;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.content.application.CourseStore;
import com.workbench.content.domain.entity.ArticleEntity;
import com.workbench.content.domain.entity.CourseEntity;
import com.workbench.content.domain.entity.CourseOutputSourceEntity;
import com.workbench.content.domain.entity.CourseSeriesEntity;
import com.workbench.content.domain.entity.MediaAssetEntity;
import com.workbench.content.facade.dto.view.CourseMediaView;
import com.workbench.content.facade.dto.view.CourseView;
import com.workbench.content.infrastructure.mapper.CourseOutputSourceMapper;
import com.workbench.content.infrastructure.mapper.MediaAssetMapper;
import com.workbench.file.facade.FileFacade;
import com.workbench.file.facade.dto.view.FileView;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 课程视图组装（含媒资批量预加载入口）。
 */
@Component
@RequiredArgsConstructor
public class CourseViewAssembler {

  /** 课程作品存储。 */
  private final CourseStore courseStore;
  /** 媒体资产表。 */
  private final MediaAssetMapper mediaAssetMapper;
  /** 课程产出同步来源。 */
  private final CourseOutputSourceMapper courseOutputSourceMapper;
  /** 媒资视图。 */
  private final MediaViewAssembler mediaViews;
  /** 文件门面。 */
  private final FileFacade files;

  /**
   * 将课程实体组装为对外 {@link CourseView}（单课路径：现场查媒资）。
   *
   * @param course       课程实体
   * @param seriesById   系列 id → 实体
   * @param articlesById 文章 id → 实体（用于字数）
   * @return 课程视图
   */
  public CourseView courseView(
      CourseEntity course,
      Map<String, CourseSeriesEntity> seriesById,
      Map<String, ArticleEntity> articlesById) {
    return courseView(course, seriesById, articlesById, null, null);
  }

  /**
   * 将课程实体组装为对外 {@link CourseView}；列表路径传入预加载媒资与文件。
   *
   * @param course        课程实体
   * @param seriesById    系列 id → 实体
   * @param articlesById  文章 id → 实体
   * @param mediaByWorkId 作品 id → 媒资列表；null 则现场查询
   * @param filesById     fileId → 文件视图；null 则逐条取文件
   * @return 课程视图
   */
  public CourseView courseView(
      CourseEntity course,
      Map<String, CourseSeriesEntity> seriesById,
      Map<String, ArticleEntity> articlesById,
      Map<String, List<MediaAssetEntity>> mediaByWorkId,
      Map<String, FileView> filesById) {
    CourseSeriesEntity series = course.getSeriesId() == null ? null : seriesById.get(course.getSeriesId());
    // 系列名缺失时从 courseId 推导
    String seriesName = series != null && series.getName() != null && !series.getName().isBlank()
        ? series.getName()
        : deriveSeriesName(course.getId());
    String draftDocumentId = course.getCurrentDraftDocumentId();
    String wechatDocumentId = course.getCurrentWechatDocumentId();
    ArticleEntity draft = draftDocumentId == null ? null : articlesById.get(draftDocumentId);
    ArticleEntity article = wechatDocumentId == null ? null : articlesById.get(wechatDocumentId);
    String draftFilePath = resolveCourseFilePath(draft);
    String articleFilePath = resolveCourseFilePath(article);
    int draftWords = draft == null || draft.getWordCount() == null ? 0 : draft.getWordCount();
    int articleWords = article == null || article.getWordCount() == null ? 0 : article.getWordCount();
    Integer lessonNo = course.getLessonNo();
    String lesson = lessonNo == null ? "" : ("第 " + lessonNo + " 课");
    List<MediaAssetEntity> mediaRows = resolveMediaRows(course.getId(), mediaByWorkId);
    List<CourseMediaView> images = mediaRows.stream()
        .map(media -> mediaViews.mediaAssetView(media, filesById))
        .toList();

    // metadata 仅 learningOutcomes / seriesPositioning，无值不输出键
    Map<String, Object> metadata = courseStore.buildMetadata(course);

    return CourseView.builder()
        .id(course.getId())
        .themeId(course.getThemeId() != null && !course.getThemeId().isBlank()
            ? course.getThemeId()
            : (series != null ? series.getThemeId() : null))
        .seriesId(course.getSeriesId())
        .series(seriesName)
        .lesson(lesson)
        .lessonNo(lessonNo)
        .title(course.getTitle())
        .summary(course.getSummary())
        .status(course.getStatus())
        .published("published".equalsIgnoreCase(course.getStatus()) || "人工已审核".equals(course.getStatus()))
        .progress(course.getProgress() == null ? 0 : course.getProgress())
        .draftWords(draftWords)
        .articleWords(articleWords)
        .images(images.size())
        .imageAssets(images)
        .imagePublicPaths(images.stream().map(CourseMediaView::getUrl).toList())
        .draftFilePath(draftFilePath)
        .articleFilePath(articleFilePath)
        .reviewScore(0)
        .updatedAt(course.getUpdatedAt() == null ? null : course.getUpdatedAt().toString())
        // API 兼容：articleId 对外等于 documentId
        .currentDraftArticleId(draftDocumentId)
        .currentWechatArticleId(wechatDocumentId)
        .currentDraftDocumentId(draftDocumentId)
        .currentWechatDocumentId(wechatDocumentId)
        .metadata(metadata)
        .build();
  }

  /**
   * 按作品 id 集合批量加载媒资，并批量解析文件元数据。
   *
   * @param workIds 作品 id 集合
   * @return 预加载结果：媒资按作品分组 + 文件索引
   */
  public CourseMediaBatch loadMediaBatch(Collection<String> workIds) {
    Map<String, List<MediaAssetEntity>> mediaByWorkId = new LinkedHashMap<>();
    Map<String, FileView> filesById = new HashMap<>();
    if (CollUtil.isEmpty(workIds)) {
      return new CourseMediaBatch(mediaByWorkId, filesById);
    }
    List<String> ids = workIds.stream().filter(StrUtil::isNotBlank).distinct().toList();
    if (ids.isEmpty()) {
      return new CourseMediaBatch(mediaByWorkId, filesById);
    }
    List<MediaAssetEntity> rows = mediaAssetMapper.selectList(new LambdaQueryWrapper<MediaAssetEntity>()
        .in(MediaAssetEntity::getWorkId, ids)
        .orderByAsc(MediaAssetEntity::getSequenceNo)
        .orderByAsc(MediaAssetEntity::getAltText));
    for (MediaAssetEntity row : rows) {
      mediaByWorkId.computeIfAbsent(row.getWorkId(), key -> new ArrayList<>()).add(row);
    }
    List<String> fileIds = rows.stream()
        .map(MediaAssetEntity::getFileId)
        .filter(StrUtil::isNotBlank)
        .distinct()
        .toList();
    if (!fileIds.isEmpty()) {
      filesById.putAll(files.getMany(fileIds));
    }
    return new CourseMediaBatch(mediaByWorkId, filesById);
  }

  /**
   * 从 courseId（如 course-系列名-课号）推导系列显示名。
   *
   * @param courseId 课程 id
   * @return 系列名，无法解析时「未分类」
   */
  public static String deriveSeriesName(String courseId) {
    // course-系列名-序号 启发式解析；失败则「未分类」
    if (courseId == null || !courseId.startsWith("course-")) {
      return "未分类";
    }
    String rest = courseId.substring("course-".length());
    int dash = rest.lastIndexOf('-');
    if (dash <= 0) {
      return "未分类";
    }
    String maybeNo = rest.substring(dash + 1);
    if (maybeNo.chars().allMatch(Character::isDigit)) {
      String name = rest.substring(0, dash).trim();
      return name.isEmpty() ? "未分类" : name;
    }
    return "未分类";
  }

  /**
   * 解析课程文章路径：优先内存投影，再读 course_output_sources。
   *
   * @param article 文章实体；可为 null
   * @return 相对路径；均可空
   */
  private String resolveCourseFilePath(ArticleEntity article) {
    if (article == null) {
      return null;
    }
    if (StrUtil.isNotBlank(article.getFilePath())) {
      return article.getFilePath();
    }
    if (StrUtil.isBlank(article.getId())) {
      return null;
    }
    CourseOutputSourceEntity source = courseOutputSourceMapper.selectById(article.getId());
    return source == null ? null : source.getSyncRelativePath();
  }

  /**
   * 解析作品媒资行：优先用预加载缓存，否则按作品 id 查库。
   *
   * @param workId         作品 id
   * @param mediaByWorkId  预加载缓存；null 表示未预加载
   * @return 媒资实体列表
   */
  private List<MediaAssetEntity> resolveMediaRows(
      String workId, Map<String, List<MediaAssetEntity>> mediaByWorkId) {
    if (mediaByWorkId != null) {
      List<MediaAssetEntity> cached = mediaByWorkId.get(workId);
      return cached == null ? List.of() : cached;
    }
    return mediaAssetMapper.selectList(new LambdaQueryWrapper<MediaAssetEntity>()
            .eq(MediaAssetEntity::getWorkId, workId)
            .orderByAsc(MediaAssetEntity::getSequenceNo)
            .orderByAsc(MediaAssetEntity::getAltText));
  }

  /**
   * 课程列表媒资预加载结果。
   *
   * @param mediaByWorkId 作品 id → 媒资行
   * @param filesById     fileId → 文件视图
   */
  public record CourseMediaBatch(
      Map<String, List<MediaAssetEntity>> mediaByWorkId,
      Map<String, FileView> filesById) {

    /**
     * @return 涉及的作品 id（便于调用方日志）
     */
    public int workCount() {
      return mediaByWorkId == null ? 0 : mediaByWorkId.size();
    }

    /**
     * @return 媒资总行数
     */
    public int mediaCount() {
      if (mediaByWorkId == null) {
        return 0;
      }
      return mediaByWorkId.values().stream().filter(Objects::nonNull).mapToInt(List::size).sum();
    }

    /**
     * 按作品 id 收集（去重）。
     *
     * @param courses 课程列表
     * @return 作品 id 集合
     */
    public static List<String> workIdsOf(List<CourseEntity> courses) {
      if (CollUtil.isEmpty(courses)) {
        return List.of();
      }
      return courses.stream()
          .map(CourseEntity::getId)
          .filter(StrUtil::isNotBlank)
          .distinct()
          .collect(Collectors.toList());
    }
  }
}
