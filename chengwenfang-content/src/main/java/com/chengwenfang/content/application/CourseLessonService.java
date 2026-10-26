package com.chengwenfang.content.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.content.application.mode.CourseModeDefinition;
import com.chengwenfang.content.domain.entity.ArticleEntity;
import com.chengwenfang.content.domain.entity.CourseEntity;
import com.chengwenfang.content.domain.entity.CourseSeriesEntity;
import com.chengwenfang.content.facade.constant.ChannelCodes;
import com.chengwenfang.content.facade.constant.CourseLessonStatuses;
import com.chengwenfang.content.facade.dto.DefaultOutputSpec;
import com.chengwenfang.content.facade.dto.view.CourseLifecycleView;
import com.chengwenfang.content.facade.dto.view.CourseShellView;
import com.chengwenfang.content.facade.dto.view.CourseView;
import com.chengwenfang.content.facade.dto.view.SeriesNeighborItemView;
import com.chengwenfang.content.facade.dto.view.SeriesNeighborsView;
import com.chengwenfang.content.facade.enums.ArticleKind;
import com.chengwenfang.content.infrastructure.assembler.CourseViewAssembler;
import com.chengwenfang.content.infrastructure.mapper.CourseSeriesMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 课程壳与课次：列表、单课、建壳、生命周期与系列邻居。
 */
@Service
@RequiredArgsConstructor
public class CourseLessonService {
  /** 课程作品存储。 */
  private final CourseStore courseStore;
  /** 课程系列表。 */
  private final CourseSeriesMapper courseSeriesMapper;
  /** 产出正文与身份解析。 */
  private final WorkContentStoreImpl workContents;
  /** 实体工具。 */
  private final ContentEntityUtil support;
  /** 视图映射。 */
  private final ContentViewMapper views;
  /** 主题可见性。 */
  private final ThemeVisibilityGuard visibility;
  /** 课程模式定义：默认产出组合。 */
  private final CourseModeDefinition courseMode;

  /**
   * 列出指定主题下的课程，按系列 sortOrder 再按 lessonNo 排序。
   *
   * @param themeId 主题 id；空则列全部
   * @return 课程视图列表
   */
  public List<CourseView> listCourses(String themeId) {
    if (themeId != null && !themeId.isBlank()) {
      visibility.assertThemeActiveById(themeId);
    }
    // 预加载系列与文章，避免在 map 中反复查库
    LambdaQueryWrapper<CourseSeriesEntity> seriesQuery = new LambdaQueryWrapper<>();
    if (themeId != null && !themeId.isBlank()) {
      seriesQuery.eq(CourseSeriesEntity::getThemeId, themeId);
    }
    Map<String, CourseSeriesEntity> seriesById = courseSeriesMapper.selectList(seriesQuery).stream()
        .collect(Collectors.toMap(CourseSeriesEntity::getId, Function.identity(), (existing, replacement) -> existing, LinkedHashMap::new));
    List<CourseEntity> courseRows = courseStore.listByThemeId(themeId);
    Map<String, ArticleEntity> articlesById = articlesByCoursePointers(courseRows);
    // 批量预加载媒资与文件，避免列表逐课查询
    CourseViewAssembler.CourseMediaBatch mediaBatch = views.courseAssembler().loadMediaBatch(
        CourseViewAssembler.CourseMediaBatch.workIdsOf(courseRows));
    // 系列缺失或无 sortOrder 时排到末尾
    Set<String> deletedThemeIds = themeId == null || themeId.isBlank()
        ? visibility.deletedThemeIds()
        : Set.of();
    return courseRows.stream()
        .filter(course -> course.getThemeId() == null || !deletedThemeIds.contains(course.getThemeId()))
        .sorted(Comparator
            .comparingInt((CourseEntity course) -> {
              CourseSeriesEntity series = course.getSeriesId() == null ? null : seriesById.get(course.getSeriesId());
              return series == null || series.getSortOrder() == null ? Integer.MAX_VALUE : series.getSortOrder();
            })
            .thenComparingInt(course -> course.getLessonNo() == null ? Integer.MAX_VALUE : course.getLessonNo()))
        .map(course -> views.courseView(
            course, seriesById, articlesById, mediaBatch.mediaByWorkId(), mediaBatch.filesById()))
        .toList();
  }

  /**
   * 列出全部课程，按系列 sortOrder 再按 lessonNo 排序。
   *
   * @return 课程视图列表
   */
  public List<CourseView> listCourses() {
    return listCourses(null);
  }

  /**
   * 获取单课概要视图。
   *
   * @param courseId 课程 id
   * @return 课程视图
   * @throws BusinessException 课程不存在时 404
   */
  public CourseView getCourse(String courseId) {
    CourseEntity course = support.requireCourse(courseId);
    visibility.assertThemeActiveForCourse(course);
    // 全量系列映射，供系列名与排序展示
    Map<String, CourseSeriesEntity> seriesById = courseSeriesMapper.selectList(null).stream()
        .collect(Collectors.toMap(CourseSeriesEntity::getId, Function.identity(), (existing, replacement) -> existing, LinkedHashMap::new));
    // 仅加载当前原稿与公众号文档投影，用于字数等摘要
    Map<String, ArticleEntity> articlesById = new HashMap<>();
    putArticlePointer(articlesById, course.getCurrentDraftDocumentId());
    putArticlePointer(articlesById, course.getCurrentWechatDocumentId());
    return views.courseView(course, seriesById, articlesById);
  }

  /**
   * 更新课程发布生命周期（published / draft），必要时先建课程壳。
   *
   * @param courseId  课程 id
   * @param published true 记为 published，否则 draft
   * @return 生命周期视图
   */
  @Transactional
  public CourseLifecycleView patchLifecycle(String courseId, boolean published) {
    // 确保课程壳存在后再改生命周期状态
    ensureCourseShell(courseId);
    CourseEntity course = support.requireCourse(courseId);
    course.setStatus(published ? CourseLessonStatuses.PUBLISHED : CourseLessonStatuses.DRAFT);
    course.setUpdatedAt(LocalDateTime.now());
    courseStore.updateById(course);
    return CourseLifecycleView.builder()
        .courseId(courseId)
        .published(published)
        .status(course.getStatus())
        .build();
  }

  /**
   * 确保课程壳存在：缺失时创建原稿与公众号文章占位并插入课程行。
   *
   * @param courseId 课程 id
   * @return 壳视图（courseId、草稿/公众号文章 id、状态）
   * @throws BusinessException courseId 为空时 400
   */
  @Transactional
  public CourseShellView ensureCourseShell(String courseId) {
    if (courseId == null || courseId.isBlank()) {
      throw new BusinessException("course_id_required", "courseId 不能为空", 400);
    }
    LocalDateTime now = LocalDateTime.now();
    CourseEntity course = courseStore.selectById(courseId);
    visibility.assertThemeActiveForCourse(course);
    if (course == null) {
      // 全新课程：写 works + course_lessons + 默认 settings
      course = new CourseEntity();
      course.setId(courseId);
      course.setTitle(courseId);
      course.setSummary("");
      course.setStatus("素材已准备");
      course.setProgress(0);
      course.setWordCountOption("2000-4000");
      course.setImageCountOption("3-5");
      course.setUpdatedAt(now);
      courseStore.insert(course);
    }
    // 产出指针落在 work_outputs；缺失则新建 id 并按模式默认组合写空壳
    String draftId = StrUtil.blankToDefault(
        resolveCourseArticleId(course, ArticleKind.COURSE_DRAFT), IdsUtil.newId());
    String wechatId = StrUtil.blankToDefault(
        resolveCourseArticleId(course, ArticleKind.WECHAT_ARTICLE), IdsUtil.newId());
    String title = StrUtil.blankToDefault(course.getTitle(), courseId);
    String status = StrUtil.blankToDefault(course.getStatus(), CourseLessonStatuses.DRAFT);
    Map<String, String> outputIdsByChannel = new LinkedHashMap<>();
    outputIdsByChannel.put(ChannelCodes.MASTER, draftId);
    outputIdsByChannel.put(ChannelCodes.WECHAT_MP, wechatId);
    for (DefaultOutputSpec spec : courseMode.defaultOutputs()) {
      String outputId = outputIdsByChannel.get(spec.getChannelCode());
      if (StrUtil.isBlank(outputId)) {
        continue;
      }
      String outputTitle = StrUtil.isBlank(spec.getTitleSuffix())
          ? title
          : title + spec.getTitleSuffix();
      workContents.ensureOutput(
          outputId,
          courseId,
          spec.getChannelCode(),
          spec.getFormatCode(),
          outputTitle,
          status);
    }
    course.setCurrentDraftDocumentId(draftId);
    course.setCurrentWechatDocumentId(wechatId);
    return CourseShellView.builder()
        .courseId(course.getId())
        .title(course.getTitle())
        .draftArticleId(draftId)
        .wechatArticleId(wechatId)
        .draftDocumentId(draftId)
        .wechatDocumentId(wechatId)
        .status(course.getStatus())
        .build();
  }

  /**
   * 查询同系列前后课与完整课程序列，供正式生成上下文使用。
   *
   * @param courseId 当前课程 id
   * @return 含 previous/following/route；无系列时三者为空
   * @throws BusinessException 课程不存在时
   */
  public SeriesNeighborsView findSeriesNeighbors(String courseId) {
    CourseEntity course = support.requireCourse(courseId);
    visibility.assertThemeActiveForCourse(course);
    SeriesNeighborsView out = SeriesNeighborsView.builder()
        .previous(null)
        .following(null)
        .route(List.of())
        .build();
    if (course.getSeriesId() == null || course.getSeriesId().isBlank()) {
      return out;
    }
    // 同系列按课序号排序，构建完整 route 并定位前后课
    List<CourseEntity> seriesCourses = courseStore.listBySeriesId(course.getSeriesId());
    List<SeriesNeighborItemView> route = new ArrayList<>();
    CourseEntity previous = null;
    CourseEntity following = null;
    for (int index = 0; index < seriesCourses.size(); index++) {
      CourseEntity row = seriesCourses.get(index);
      route.add(SeriesNeighborItemView.builder()
          .id(row.getId())
          .title(row.getTitle())
          .summary(row.getSummary() == null ? "" : row.getSummary())
          .lessonNo(row.getLessonNo())
          .build());
      if (courseId.equals(row.getId())) {
        if (index > 0) {
          previous = seriesCourses.get(index - 1);
        }
        if (index + 1 < seriesCourses.size()) {
          following = seriesCourses.get(index + 1);
        }
      }
    }
    out.setRoute(route);
    if (previous != null) {
      out.setPrevious(SeriesNeighborItemView.builder()
          .id(previous.getId())
          .title(previous.getTitle())
          .summary(previous.getSummary() == null ? "" : previous.getSummary())
          .lessonNo(previous.getLessonNo())
          .build());
    }
    if (following != null) {
      out.setFollowing(SeriesNeighborItemView.builder()
          .id(following.getId())
          .title(following.getTitle())
          .summary(following.getSummary() == null ? "" : following.getSummary())
          .lessonNo(following.getLessonNo())
          .build());
    }
    return out;
  }

  /**
   * 将上一课正文中的「下节课预告」段落同步为当前课标题。
   *
   * @param courseId 当前课程 id
   * @throws BusinessException 当前课程不存在时
   */
  @Transactional
  public void syncPreviousLessonPreview(String courseId) {
    CourseEntity course = support.requireCourse(courseId);
    if (course.getSeriesId() == null || course.getLessonNo() == null) {
      return;
    }
    CourseEntity previous = courseStore.findBySeriesAndLessonNo(
        course.getSeriesId(), course.getLessonNo() - 1);
    if (previous == null) {
      return;
    }
    String replacement = "## 下节课预告\n\n下一课：" + course.getTitle() + "\n";
    for (String articleId : List.of(
        previous.getCurrentDraftDocumentId(), previous.getCurrentWechatDocumentId())) {
      if (StrUtil.isBlank(articleId)) {
        continue;
      }
      ArticleEntity article = support.findArticleOrNull(articleId);
      if (article == null) {
        continue;
      }
      String body = workContents.readCurrent(article.getId());
      if (StrUtil.isBlank(body)) {
        body = article.getContent() == null ? "" : article.getContent();
      }
      if (!body.contains("下节课预告")) {
        continue;
      }
      String updated = body.replaceAll(
          "(?s)##\\s*下节课预告.*?(?=\\n##\\s|\\Z)",
          java.util.regex.Matcher.quoteReplacement(replacement));
      if (!updated.equals(body)) {
        workContents.appendVersion(
            article.getId(), updated, "ai_generation", "同步下一课预告", null);
      }
    }
  }

  /**
   * 解析课程轨产出 id：sole-read work_outputs；聚合上缓存指针作回退。
   *
   * @param course 课程实体
   * @param kind   文章 kind
   * @return 产出 id；均可空
   */
  public String resolveCourseArticleId(CourseEntity course, ArticleKind kind) {
    if (course == null || kind == null) {
      return null;
    }
    String fromOutputs = courseStore.resolveDocumentId(course.getId(), kind);
    if (StrUtil.isNotBlank(fromOutputs)) {
      return fromOutputs;
    }
    return ArticleKind.COURSE_DRAFT.equals(kind)
        ? course.getCurrentDraftDocumentId()
        : course.getCurrentWechatDocumentId();
  }

  /**
   * 按课程指针批量加载文章投影，供课表视图组装。
   *
   * @param courses 课程列表
   * @return id → 文章投影
   */
  private Map<String, ArticleEntity> articlesByCoursePointers(List<CourseEntity> courses) {
    Map<String, ArticleEntity> articlesById = new HashMap<>();
    if (courses == null) {
      return articlesById;
    }
    for (CourseEntity course : courses) {
      putArticlePointer(articlesById, course.getCurrentDraftDocumentId());
      putArticlePointer(articlesById, course.getCurrentWechatDocumentId());
    }
    return articlesById;
  }

  /**
   * 将单个指针 id 解析为文章投影并写入索引。
   *
   * @param articlesById 目标索引
   * @param articleId    文章/文档 id
   */
  private void putArticlePointer(Map<String, ArticleEntity> articlesById, String articleId) {
    if (StrUtil.isBlank(articleId) || articlesById.containsKey(articleId)) {
      return;
    }
    ArticleEntity article = support.findArticleOrNull(articleId);
    if (article != null) {
      support.overlayVersionIdentityFromDocuments(article);
      articlesById.put(articleId, article);
    }
  }
}
