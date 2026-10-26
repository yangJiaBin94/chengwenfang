package com.chengwenfang.content.application;

import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.content.domain.entity.ContentThemeEntity;
import com.chengwenfang.content.domain.entity.CourseEntity;
import com.chengwenfang.content.domain.entity.CourseSeriesEntity;
import com.chengwenfang.content.facade.dto.view.CurriculumDraftView;
import com.chengwenfang.content.facade.dto.view.CurriculumLessonDraftView;
import com.chengwenfang.content.facade.dto.view.CurriculumSeriesDraftView;
import com.chengwenfang.content.facade.dto.view.ThemeCurriculumCommitView;
import com.chengwenfang.content.facade.enums.ContentThemeStatus;
import com.chengwenfang.content.infrastructure.mapper.ContentThemeMapper;
import com.chengwenfang.content.infrastructure.mapper.CourseSeriesMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 课表确认提交：落库系列与课壳并启用主题。
 */
@Slf4j
@Service
public class CurriculumCommitService {
  /** 主题表。 */
  private final ContentThemeMapper themeMapper;
  /** 主题画像结构化存储。 */
  private final ThemeProfileStore profileStore;
  /** 课表草案与路线叙事存储。 */
  private final CourseCurriculumStore curriculumStore;
  /** 课程系列表。 */
  private final CourseSeriesMapper courseSeriesMapper;
  /** 课程作品存储。 */
  private final CourseStore courseStore;
  /** 主题生命周期（解析）。 */
  private final ThemeLifecycleService lifecycle;
  /** 课表草案校验。 */
  private final CurriculumDraftService drafts;
  /** 默认偏好提炼。 */
  private final ThemeDefaultsBuilder defaultsBuilder;
  /** 课程壳（建壳）。 */
  private final CourseShellService shell;

  /**
   * @param themeMapper       主题表
   * @param profileStore      画像存储
   * @param curriculumStore   课表存储
   * @param courseSeriesMapper 系列表
   * @param courseStore       课程存储
   * @param lifecycle         主题生命周期
   * @param drafts            草案服务
   * @param defaultsBuilder   默认偏好
   * @param shell             课程壳（懒加载避免环）
   */
  public CurriculumCommitService(
      ContentThemeMapper themeMapper,
      ThemeProfileStore profileStore,
      CourseCurriculumStore curriculumStore,
      CourseSeriesMapper courseSeriesMapper,
      CourseStore courseStore,
      ThemeLifecycleService lifecycle,
      CurriculumDraftService drafts,
      ThemeDefaultsBuilder defaultsBuilder,
      @Lazy CourseShellService shell) {
    this.themeMapper = themeMapper;
    this.profileStore = profileStore;
    this.curriculumStore = curriculumStore;
    this.courseSeriesMapper = courseSeriesMapper;
    this.courseStore = courseStore;
    this.lifecycle = lifecycle;
    this.drafts = drafts;
    this.defaultsBuilder = defaultsBuilder;
    this.shell = shell;
  }

  /**
   * 确认课表并落库系列与课壳。
   *
   * @param themeId 主题 id/code
   * @return commit 结果
   */
  @Transactional
  public ThemeCurriculumCommitView commitCurriculum(String themeId) {
    ContentThemeEntity theme = lifecycle.requireActiveTheme(themeId);
    if (ContentThemeStatus.ENABLED.getValue().equals(theme.getStatus())
        || ContentThemeStatus.DISABLED.getValue().equals(theme.getStatus())) {
      throw new BusinessException(ErrorCodes.CURRICULUM_ALREADY_COMMITTED, "主题课表已确认，不可重复提交", 409);
    }
    CurriculumDraftView draft = drafts.validateDraft(curriculumStore.loadDraft(theme.getId()));
    LocalDateTime now = LocalDateTime.now();
    int courseCount = 0;

    // 从问卷推导生成偏好默认，写入 defaults 画像
    Map<String, Object> defaults = defaultsBuilder.buildDefaultsFromBriefing(
        profileStore.loadBriefingJson(theme.getId()));
    profileStore.saveDefaults(theme.getId(), defaults, now);

    for (CurriculumSeriesDraftView seriesDraft : draft.getSeries()) {
      CourseSeriesEntity series = new CourseSeriesEntity();
      series.setId("series-" + IdsUtil.newId().replace("-", "").substring(0, 16));
      series.setThemeId(theme.getId());
      series.setName(seriesDraft.getName().trim());
      series.setSortOrder(seriesDraft.getSortOrder());
      series.setCreatedAt(now);
      series.setUpdatedAt(now);
      courseSeriesMapper.insert(series);

      for (CurriculumLessonDraftView lesson : seriesDraft.getLessons()) {
        String courseId = "course-" + theme.getCode() + "-" + series.getId().substring(7, 15)
            + "-" + lesson.getLessonNo();
        if (courseId.length() > 120) {
          courseId = "course-" + IdsUtil.newId();
        }
        // 建壳（文章占位）
        shell.ensureCourseShell(courseId);
        CourseEntity course = courseStore.selectById(courseId);
        course.setThemeId(theme.getId());
        course.setSeriesId(series.getId());
        course.setLessonNo(lesson.getLessonNo());
        course.setTitle(lesson.getTitle().trim());
        course.setSummary(lesson.getSummary() == null ? "" : lesson.getSummary().trim());
        course.setStatus("素材已准备");
        course.setProgress(0);
        course.touchSeriesPositioning(seriesDraft.getPositioning());
        course.touchLearningOutcomes(
            lesson.getLearningOutcomes() == null ? List.of() : lesson.getLearningOutcomes());
        course.setUpdatedAt(now);
        courseStore.updateById(course);
        courseCount++;
      }
    }

    curriculumStore.saveRouteNarrative(theme.getId(), draft.getRouteNarrative(), now);
    theme.setStatus(ContentThemeStatus.ENABLED.getValue());
    theme.setUpdatedAt(now);
    themeMapper.updateById(theme);
    log.info("主题课表已确认 themeId={} series={} courses={}", theme.getId(), draft.getSeries().size(), courseCount);
    return ThemeCurriculumCommitView.builder()
        .themeId(theme.getId())
        .status(theme.getStatus())
        .seriesCount(draft.getSeries().size())
        .courseCount(courseCount)
        .build();
  }
}
