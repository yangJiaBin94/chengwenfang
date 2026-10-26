package com.workbench.content.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.common.IdsUtil;
import com.workbench.content.domain.entity.CourseLessonEntity;
import com.workbench.content.domain.entity.CourseLessonOutcomeEntity;
import com.workbench.content.domain.entity.CourseEntity;
import com.workbench.content.domain.entity.WorkEntity;
import com.workbench.content.domain.entity.WorkGenerationSettingsEntity;
import com.workbench.content.application.mode.CourseModeDefinition;
import com.workbench.content.facade.constant.ContentModeIds;
import com.workbench.content.facade.constant.CourseOutputChannels;
import com.workbench.content.facade.dto.WorkExtensionPayload;
import com.workbench.content.facade.enums.ArticleKind;
import com.workbench.content.infrastructure.mapper.CourseLessonMapper;
import com.workbench.content.infrastructure.mapper.CourseLessonOutcomeMapper;
import com.workbench.content.infrastructure.mapper.WorkGenerationSettingsMapper;
import com.workbench.content.infrastructure.mapper.WorkMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 课程作品读写：组装 works + course_lessons + settings + outcomes + 文档指针。
 */
@Service
public class CourseStore {
  /** 已完成态状态值（主题进度统计）。 */
  private static final List<String> COMPLETED_STATUSES =
      List.of("人工已审核", "reviewed", "approved", "published");

  private final WorkMapper workMapper;
  private final CourseLessonMapper lessonMapper;
  private final WorkGenerationSettingsMapper settingsMapper;
  private final CourseLessonOutcomeMapper outcomeMapper;
  private final WorkContentStoreImpl workContents;
  /** 课程模式定义：创建扩展行。 */
  private final CourseModeDefinition courseMode;

  /**
   * @param workMapper     作品
   * @param lessonMapper   课扩展
   * @param settingsMapper 生成设置
   * @param outcomeMapper  学习成果
   * @param workContents   产出正文与指针
   * @param courseMode     课程模式定义
   */
  public CourseStore(
      WorkMapper workMapper,
      CourseLessonMapper lessonMapper,
      WorkGenerationSettingsMapper settingsMapper,
      CourseLessonOutcomeMapper outcomeMapper,
      WorkContentStoreImpl workContents,
      CourseModeDefinition courseMode) {
    this.workMapper = workMapper;
    this.lessonMapper = lessonMapper;
    this.settingsMapper = settingsMapper;
    this.outcomeMapper = outcomeMapper;
    this.workContents = workContents;
    this.courseMode = courseMode;
  }

  /**
   * 按作品 id 加载课程聚合；缺失返回 null。
   *
   * @param workId 作品/课程 id
   * @return 聚合；不存在为 null
   */
  public CourseEntity selectById(String workId) {
    if (StrUtil.isBlank(workId)) {
      return null;
    }
    WorkEntity work = workMapper.selectById(workId);
    if (work == null) {
      return null;
    }
    CourseLessonEntity lesson = lessonMapper.selectById(workId);
    WorkGenerationSettingsEntity settings = settingsMapper.selectById(workId);
    return assemble(work, lesson, settings, true);
  }

  /**
   * 按主题 id 列出课程聚合。
   *
   * @param themeId 主题 id；空则全部课程作品
   * @return 列表
   */
  public List<CourseEntity> listByThemeId(String themeId) {
    LambdaQueryWrapper<WorkEntity> query = new LambdaQueryWrapper<WorkEntity>()
        .eq(WorkEntity::getModeCode, ContentModeIds.COURSE_SYSTEM_CODE);
    if (StrUtil.isNotBlank(themeId)) {
      query.eq(WorkEntity::getThemeId, themeId);
    }
    List<WorkEntity> works = workMapper.selectList(query);
    return assembleAll(works, true);
  }

  /**
   * 按系列 id 列出课程，按课序号升序。
   *
   * @param seriesId 系列 id
   * @return 列表
   */
  public List<CourseEntity> listBySeriesId(String seriesId) {
    if (StrUtil.isBlank(seriesId)) {
      return List.of();
    }
    List<CourseLessonEntity> lessons = lessonMapper.selectList(new LambdaQueryWrapper<CourseLessonEntity>()
        .eq(CourseLessonEntity::getSeriesId, seriesId)
        .orderByAsc(CourseLessonEntity::getLessonNo)
        .orderByAsc(CourseLessonEntity::getWorkId));
    if (CollUtil.isEmpty(lessons)) {
      return List.of();
    }
    Set<String> workIds = lessons.stream().map(CourseLessonEntity::getWorkId).collect(Collectors.toSet());
    Map<String, WorkEntity> worksById = workMapper.selectList(new LambdaQueryWrapper<WorkEntity>()
            .in(WorkEntity::getId, workIds)).stream()
        .collect(Collectors.toMap(WorkEntity::getId, work -> work, (left, right) -> left, LinkedHashMap::new));
    Map<String, WorkGenerationSettingsEntity> settingsById = settingsMapper.selectList(
            new LambdaQueryWrapper<WorkGenerationSettingsEntity>()
                .in(WorkGenerationSettingsEntity::getWorkId, workIds)).stream()
        .collect(Collectors.toMap(
            WorkGenerationSettingsEntity::getWorkId, settings -> settings, (left, right) -> left));
    List<CourseEntity> result = new ArrayList<>();
    for (CourseLessonEntity lesson : lessons) {
      WorkEntity work = worksById.get(lesson.getWorkId());
      if (work == null) {
        continue;
      }
      result.add(assemble(work, lesson, settingsById.get(lesson.getWorkId()), true));
    }
    return result;
  }

  /**
   * 统计主题下课程数。
   *
   * @param themeId 主题 id
   * @return 数量
   */
  public long countByThemeId(String themeId) {
    if (StrUtil.isBlank(themeId)) {
      return 0L;
    }
    Long count = workMapper.selectCount(new LambdaQueryWrapper<WorkEntity>()
        .eq(WorkEntity::getModeCode, ContentModeIds.COURSE_SYSTEM_CODE)
        .eq(WorkEntity::getThemeId, themeId));
    return count == null ? 0L : count;
  }

  /**
   * 统计主题下已完成课程数。
   *
   * @param themeId 主题 id
   * @return 数量
   */
  public long countCompletedByThemeId(String themeId) {
    if (StrUtil.isBlank(themeId)) {
      return 0L;
    }
    return countCompletedByThemeIds(List.of(themeId)).getOrDefault(themeId, 0L);
  }

  /**
   * 批量统计各主题下课程数（一次 IN 查询）。
   *
   * @param themeIds 主题 id 集合
   * @return themeId → 数量
   */
  public Map<String, Long> countByThemeIds(Collection<String> themeIds) {
    Map<String, Long> counts = new LinkedHashMap<>();
    if (CollUtil.isEmpty(themeIds)) {
      return counts;
    }
    List<String> ids = themeIds.stream().filter(StrUtil::isNotBlank).distinct().toList();
    if (ids.isEmpty()) {
      return counts;
    }
    for (String themeId : ids) {
      counts.put(themeId, 0L);
    }
    List<WorkEntity> works = workMapper.selectList(new LambdaQueryWrapper<WorkEntity>()
        .eq(WorkEntity::getModeCode, ContentModeIds.COURSE_SYSTEM_CODE)
        .in(WorkEntity::getThemeId, ids)
        .select(WorkEntity::getThemeId));
    for (WorkEntity work : works) {
      if (work != null && StrUtil.isNotBlank(work.getThemeId())) {
        counts.merge(work.getThemeId(), 1L, Long::sum);
      }
    }
    return counts;
  }

  /**
   * 批量统计各主题下已完成课程数（完成课扩展一次查 + 作品一次 IN）。
   *
   * @param themeIds 主题 id 集合
   * @return themeId → 数量
   */
  public Map<String, Long> countCompletedByThemeIds(Collection<String> themeIds) {
    Map<String, Long> counts = new LinkedHashMap<>();
    if (CollUtil.isEmpty(themeIds)) {
      return counts;
    }
    List<String> ids = themeIds.stream().filter(StrUtil::isNotBlank).distinct().toList();
    if (ids.isEmpty()) {
      return counts;
    }
    for (String themeId : ids) {
      counts.put(themeId, 0L);
    }
    List<CourseLessonEntity> lessons = lessonMapper.selectList(new LambdaQueryWrapper<CourseLessonEntity>()
        .in(CourseLessonEntity::getStatus, COMPLETED_STATUSES));
    if (CollUtil.isEmpty(lessons)) {
      return counts;
    }
    Set<String> workIds = lessons.stream().map(CourseLessonEntity::getWorkId).collect(Collectors.toSet());
    if (workIds.isEmpty()) {
      return counts;
    }
    List<WorkEntity> works = workMapper.selectList(new LambdaQueryWrapper<WorkEntity>()
        .eq(WorkEntity::getModeCode, ContentModeIds.COURSE_SYSTEM_CODE)
        .in(WorkEntity::getThemeId, ids)
        .in(WorkEntity::getId, workIds)
        .select(WorkEntity::getThemeId));
    for (WorkEntity work : works) {
      if (work != null && StrUtil.isNotBlank(work.getThemeId())) {
        counts.merge(work.getThemeId(), 1L, Long::sum);
      }
    }
    return counts;
  }

  /**
   * 统计系列下课数。
   *
   * @param seriesId 系列 id
   * @return 数量
   */
  public long countBySeriesId(String seriesId) {
    if (StrUtil.isBlank(seriesId)) {
      return 0L;
    }
    Long count = lessonMapper.selectCount(new LambdaQueryWrapper<CourseLessonEntity>()
        .eq(CourseLessonEntity::getSeriesId, seriesId));
    return count == null ? 0L : count;
  }

  /**
   * 按系列与课序号取一课。
   *
   * @param seriesId 系列 id
   * @param lessonNo 课序号
   * @return 聚合；缺失 null
   */
  public CourseEntity findBySeriesAndLessonNo(String seriesId, int lessonNo) {
    CourseLessonEntity lesson = lessonMapper.selectOne(new LambdaQueryWrapper<CourseLessonEntity>()
        .eq(CourseLessonEntity::getSeriesId, seriesId)
        .eq(CourseLessonEntity::getLessonNo, lessonNo)
        .last("LIMIT 1"));
    if (lesson == null) {
      return null;
    }
    return selectById(lesson.getWorkId());
  }

  /**
   * 新建作品壳：works + lesson + 默认 settings。
   *
   * @param course 聚合（须含 id）
   */
  @Transactional
  public void insert(CourseEntity course) {
    LocalDateTime now = course.getUpdatedAt() != null ? course.getUpdatedAt() : LocalDateTime.now();
    WorkEntity work = WorkEntity.builder()
        .id(course.getId())
        .modeCode(courseMode.modeCode())
        .themeId(course.getThemeId())
        .title(StrUtil.blankToDefault(course.getTitle(), course.getId()))
        .createdAt(now)
        .updatedAt(now)
        .build();
    workMapper.insert(work);
    // 课次 / 设置 / 学习成果交由模式定义创建
    courseMode.createExtension(WorkExtensionPayload.builder()
        .workId(course.getId())
        .themeId(course.getThemeId())
        .title(course.getTitle())
        .status(StrUtil.blankToDefault(course.getStatus(), "draft"))
        .updatedAt(now)
        .course(WorkExtensionPayload.CourseLessonExtension.builder()
            .seriesId(course.getSeriesId())
            .lessonNo(course.getLessonNo())
            .summary(course.getSummary())
            .progress(course.getProgress())
            .seriesPositioning(course.getSeriesPositioning())
            .wordCountOption(course.getWordCountOption())
            .imageCountOption(course.getImageCountOption())
            .recommendedWordCountOption(course.getRecommendedWordCountOption())
            .recommendedImageCountOption(course.getRecommendedImageCountOption())
            .recommendationReason(course.getRecommendationReason())
            .recommendedAt(course.getRecommendedAt())
            .preferenceFingerprint(course.getPreferenceFingerprint())
            .learningOutcomes(course.getLearningOutcomes())
            .build())
        .build());
  }

  /**
   * 更新作品/课/设置；学习成果若非 null 则整表替换。
   *
   * @param course 聚合
   */
  @Transactional
  public void updateById(CourseEntity course) {
    if (course == null || StrUtil.isBlank(course.getId())) {
      return;
    }
    LocalDateTime now = course.getUpdatedAt() != null ? course.getUpdatedAt() : LocalDateTime.now();
    WorkEntity work = workMapper.selectById(course.getId());
    if (work == null) {
      return;
    }
    work.setThemeId(course.getThemeId());
    work.setTitle(StrUtil.blankToDefault(course.getTitle(), work.getTitle()));
    work.setUpdatedAt(now);
    workMapper.updateById(work);

    CourseLessonEntity lesson = lessonMapper.selectById(course.getId());
    if (lesson == null) {
      lesson = CourseLessonEntity.builder().workId(course.getId()).build();
      lessonMapper.insert(lesson);
    }
    lesson.setSeriesId(course.getSeriesId());
    lesson.setLessonNo(course.getLessonNo());
    lesson.setSummary(course.getSummary());
    lesson.setStatus(course.getStatus());
    lesson.setProgress(course.getProgress());
    if (course.getSeriesPositioning() != null || course.isSeriesPositioningTouched()) {
      lesson.setSeriesPositioning(course.getSeriesPositioning());
    }
    lessonMapper.updateById(lesson);

    WorkGenerationSettingsEntity settings = settingsMapper.selectById(course.getId());
    if (settings == null) {
      settings = WorkGenerationSettingsEntity.builder().workId(course.getId()).build();
      settingsMapper.insert(settings);
    }
    if (course.getWordCountOption() != null) {
      settings.setWordCountOption(course.getWordCountOption());
    }
    if (course.getImageCountOption() != null) {
      settings.setImageCountOption(course.getImageCountOption());
    }
    if (course.getRecommendedWordCountOption() != null) {
      settings.setRecommendedWordCountOption(course.getRecommendedWordCountOption());
    }
    if (course.getRecommendedImageCountOption() != null) {
      settings.setRecommendedImageCountOption(course.getRecommendedImageCountOption());
    }
    if (course.getRecommendationReason() != null) {
      settings.setRecommendationReason(course.getRecommendationReason());
    }
    if (course.getRecommendedAt() != null) {
      settings.setRecommendedAt(course.getRecommendedAt());
    }
    if (course.getPreferenceFingerprint() != null) {
      settings.setPreferenceFingerprint(course.getPreferenceFingerprint());
    }
    settings.setUpdatedAt(now);
    settingsMapper.updateById(settings);

    if (course.isLearningOutcomesTouched()) {
      replaceOutcomes(course.getId(), course.getLearningOutcomes());
    }
  }

  /**
   * 组装 CourseView.metadata：仅有值时输出 learningOutcomes / seriesPositioning。
   *
   * @param course 聚合
   * @return metadata Map（可能空）
   */
  public Map<String, Object> buildMetadata(CourseEntity course) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    if (course == null) {
      return metadata;
    }
    List<String> outcomes = course.getLearningOutcomes();
    if (outcomes == null) {
      outcomes = listOutcomeTexts(course.getId());
    }
    if (CollUtil.isNotEmpty(outcomes)) {
      metadata.put("learningOutcomes", outcomes);
    }
    String positioning = course.getSeriesPositioning();
    if (StrUtil.isNotBlank(positioning)) {
      metadata.put("seriesPositioning", positioning);
    }
    return metadata;
  }

  /**
   * 写入系列定位与学习成果（课表提交用）。
   *
   * @param workId             作品 id
   * @param seriesPositioning  系列定位；可为 null
   * @param learningOutcomes   成果列表；null 表示不清空
   */
  @Transactional
  public void saveLessonMetadata(String workId, String seriesPositioning, List<String> learningOutcomes) {
    CourseLessonEntity lesson = lessonMapper.selectById(workId);
    if (lesson != null) {
      lesson.setSeriesPositioning(seriesPositioning);
      lessonMapper.updateById(lesson);
    }
    if (learningOutcomes != null) {
      replaceOutcomes(workId, learningOutcomes);
    }
  }

  /**
   * 解析产出指针：sole-read work_outputs（work_id + channel + format）。
   *
   * @param workId 作品 id
   * @param kind   文章 kind
   * @return 产出 id；缺失 null
   */
  public String resolveDocumentId(String workId, ArticleKind kind) {
    if (StrUtil.isBlank(workId) || kind == null || workContents == null) {
      return null;
    }
    return workContents.findOutputId(workId, CourseOutputChannels.channelCode(kind));
  }

  /**
   * 批量组装。
   *
   * @param works           作品行
   * @param resolvePointers 是否解析文档指针
   * @return 聚合列表
   */
  private List<CourseEntity> assembleAll(Collection<WorkEntity> works, boolean resolvePointers) {
    if (CollUtil.isEmpty(works)) {
      return List.of();
    }
    Set<String> workIds = works.stream().map(WorkEntity::getId).filter(Objects::nonNull).collect(Collectors.toSet());
    Map<String, CourseLessonEntity> lessonsById = lessonMapper.selectList(new LambdaQueryWrapper<CourseLessonEntity>()
            .in(CourseLessonEntity::getWorkId, workIds)).stream()
        .collect(Collectors.toMap(CourseLessonEntity::getWorkId, lesson -> lesson, (left, right) -> left));
    Map<String, WorkGenerationSettingsEntity> settingsById = settingsMapper.selectList(
            new LambdaQueryWrapper<WorkGenerationSettingsEntity>()
                .in(WorkGenerationSettingsEntity::getWorkId, workIds)).stream()
        .collect(Collectors.toMap(
            WorkGenerationSettingsEntity::getWorkId, settings -> settings, (left, right) -> left));
    List<CourseEntity> result = new ArrayList<>();
    for (WorkEntity work : works) {
      result.add(assemble(work, lessonsById.get(work.getId()), settingsById.get(work.getId()), resolvePointers));
    }
    return result;
  }

  /**
   * 组装单课聚合。
   *
   * @param work            作品
   * @param lesson          课扩展；可空
   * @param settings        设置；可空
   * @param resolvePointers 是否查文档指针
   * @return 聚合
   */
  private CourseEntity assemble(
      WorkEntity work,
      CourseLessonEntity lesson,
      WorkGenerationSettingsEntity settings,
      boolean resolvePointers) {
    CourseEntity course = new CourseEntity();
    course.setId(work.getId());
    course.setThemeId(work.getThemeId());
    course.setTitle(work.getTitle());
    course.setUpdatedAt(work.getUpdatedAt());
    if (lesson != null) {
      course.setSeriesId(lesson.getSeriesId());
      course.setLessonNo(lesson.getLessonNo());
      course.setSummary(lesson.getSummary());
      course.setStatus(lesson.getStatus());
      course.setProgress(lesson.getProgress());
      course.setSeriesPositioning(lesson.getSeriesPositioning());
    }
    if (settings != null) {
      course.setWordCountOption(settings.getWordCountOption());
      course.setImageCountOption(settings.getImageCountOption());
      course.setRecommendedWordCountOption(settings.getRecommendedWordCountOption());
      course.setRecommendedImageCountOption(settings.getRecommendedImageCountOption());
      course.setRecommendationReason(settings.getRecommendationReason());
      course.setRecommendedAt(settings.getRecommendedAt());
      course.setPreferenceFingerprint(settings.getPreferenceFingerprint());
    }
    if (resolvePointers) {
      course.setCurrentDraftDocumentId(resolveDocumentId(work.getId(), ArticleKind.COURSE_DRAFT));
      course.setCurrentWechatDocumentId(resolveDocumentId(work.getId(), ArticleKind.WECHAT_ARTICLE));
    }
    return course;
  }

  /**
   * 读取学习成果正文列表。
   *
   * @param workId 作品 id
   * @return 列表
   */
  private List<String> listOutcomeTexts(String workId) {
    if (StrUtil.isBlank(workId)) {
      return List.of();
    }
    return outcomeMapper.selectList(new LambdaQueryWrapper<CourseLessonOutcomeEntity>()
            .eq(CourseLessonOutcomeEntity::getWorkId, workId)
            .orderByAsc(CourseLessonOutcomeEntity::getSeq))
        .stream()
        .map(CourseLessonOutcomeEntity::getContent)
        .filter(StrUtil::isNotBlank)
        .toList();
  }

  /**
   * 整表替换学习成果。
   *
   * @param workId   作品 id
   * @param outcomes 成果；null/空则清空
   */
  private void replaceOutcomes(String workId, List<String> outcomes) {
    outcomeMapper.delete(new LambdaQueryWrapper<CourseLessonOutcomeEntity>()
        .eq(CourseLessonOutcomeEntity::getWorkId, workId));
    if (CollUtil.isEmpty(outcomes)) {
      return;
    }
    int seq = 0;
    for (String text : outcomes) {
      if (StrUtil.isBlank(text)) {
        continue;
      }
      seq++;
      outcomeMapper.insert(CourseLessonOutcomeEntity.builder()
          .id(IdsUtil.newId())
          .workId(workId)
          .seq(seq)
          .content(text.trim())
          .build());
    }
  }
}
