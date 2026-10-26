package com.chengwenfang.content.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.content.domain.entity.ContentThemeEntity;
import com.chengwenfang.content.facade.dto.request.CurriculumDraftSaveRequest;
import com.chengwenfang.content.facade.dto.view.CurriculumDraftView;
import com.chengwenfang.content.facade.dto.view.CurriculumLessonDraftView;
import com.chengwenfang.content.facade.dto.view.CurriculumSeriesDraftView;
import com.chengwenfang.content.facade.dto.view.ThemeCurriculumGenerateView;
import com.chengwenfang.content.facade.enums.ContentThemeStatus;
import com.chengwenfang.content.infrastructure.mapper.ContentThemeMapper;
import com.chengwenfang.job.facade.JobFacade;
import com.chengwenfang.job.facade.constant.JobEntityTypes;
import com.chengwenfang.job.facade.constant.JobTypes;
import com.chengwenfang.job.facade.dto.job.ThemeCurriculumGenerationJobInput;
import java.time.LocalDateTime;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 课表草案读写、校验与生成任务入队。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CurriculumDraftService {
  /** 系列数量上限。 */
  private static final int MAX_SERIES = 20;
  /** 单系列课次上限。 */
  private static final int MAX_LESSONS_PER_SERIES = 30;
  /** 主题总课次上限。 */
  private static final int MAX_TOTAL_LESSONS = 200;

  /** 主题表。 */
  private final ContentThemeMapper themeMapper;
  /** 主题画像结构化存储。 */
  private final ThemeProfileStore profileStore;
  /** 课表草案与路线叙事存储。 */
  private final CourseCurriculumStore curriculumStore;
  /** 任务门面。 */
  private final JobFacade jobs;
  /** 主题生命周期（解析与守卫）。 */
  private final ThemeLifecycleService lifecycle;
  /** JSON 序列化。 */
  private final ObjectMapper objectMapper;
  /** 实体与 JSON 工具。 */
  private final ContentEntityUtil support;

  /**
   * 入队课表生成任务。
   *
   * @param themeId 主题 id/code
   * @return job 视图
   */
  @Transactional
  public ThemeCurriculumGenerateView enqueueCurriculumGenerate(String themeId) {
    ContentThemeEntity theme = lifecycle.requireActiveTheme(themeId);
    lifecycle.assertEditableBriefing(theme);
    if (profileStore.loadBriefing(theme.getId()).isEmpty()) {
      throw new BusinessException(ErrorCodes.CURRICULUM_DRAFT_INVALID, "请先完成问卷后再生成课表", 400);
    }
    String jobId = jobs.create(
            JobTypes.THEME_CURRICULUM_GENERATION,
            JobEntityTypes.THEME,
            theme.getId(),
            ThemeCurriculumGenerationJobInput.builder()
                .themeId(theme.getId())
                .build())
        .getId();
    theme.setUpdatedAt(LocalDateTime.now());
    themeMapper.updateById(theme);
    log.info("课表生成任务已入队 themeId={} jobId={}", theme.getId(), jobId);
    return ThemeCurriculumGenerateView.builder()
        .themeId(theme.getId())
        .jobId(jobId)
        .status(theme.getStatus())
        .build();
  }

  /**
   * 读取课表草案。
   *
   * @param themeId 主题 id/code
   * @return 草案；无则空结构
   */
  public CurriculumDraftView getCurriculumDraft(String themeId) {
    ContentThemeEntity theme = lifecycle.requireActiveTheme(themeId);
    return curriculumStore.loadDraft(theme.getId());
  }

  /**
   * 人工保存课表草案。
   *
   * @param themeId 主题 id/code
   * @param request 草案
   * @return 校验后的草案
   */
  @Transactional
  public CurriculumDraftView saveCurriculumDraft(String themeId, CurriculumDraftSaveRequest request) {
    ContentThemeEntity theme = lifecycle.requireActiveTheme(themeId);
    lifecycle.assertEditableBriefing(theme);
    if (request == null || request.getDraft() == null) {
      throw new BusinessException(ErrorCodes.CURRICULUM_DRAFT_INVALID, "课表草案不能为空", 400);
    }
    CurriculumDraftView draft = validateDraft(request.getDraft());
    LocalDateTime now = LocalDateTime.now();
    curriculumStore.saveDraft(theme.getId(), draft, now);
    theme.setStatus(ContentThemeStatus.READY_FOR_REVIEW.getValue());
    theme.setUpdatedAt(now);
    themeMapper.updateById(theme);
    return draft;
  }

  /**
   * Job/编排写入 AI 生成的课表草案（覆盖未确认草案）。
   *
   * @param themeId 主题 id
   * @param draft   草案
   */
  @Transactional
  public void applyGeneratedCurriculum(String themeId, CurriculumDraftView draft) {
    ContentThemeEntity theme = lifecycle.requireActiveTheme(themeId);
    if (ContentThemeStatus.ENABLED.getValue().equals(theme.getStatus())
        || ContentThemeStatus.DISABLED.getValue().equals(theme.getStatus())) {
      throw new BusinessException(ErrorCodes.CURRICULUM_ALREADY_COMMITTED, "主题已确认，不能覆盖课表草案", 409);
    }
    CurriculumDraftView validated = validateDraft(draft);
    LocalDateTime now = LocalDateTime.now();
    curriculumStore.saveDraft(theme.getId(), validated, now);
    curriculumStore.saveRouteNarrative(theme.getId(), validated.getRouteNarrative(), now);
    theme.setStatus(ContentThemeStatus.READY_FOR_REVIEW.getValue());
    theme.setUpdatedAt(now);
    themeMapper.updateById(theme);
    log.info("已写入课表草案 themeId={} series={}", themeId, validated.getSeries().size());
  }

  /**
   * 校验课表草案结构与数量上限，并回填 sortOrder / lessonNo。
   *
   * @param draft 草案
   * @return 校验后的草案
   */
  public CurriculumDraftView validateDraft(CurriculumDraftView draft) {
    if (draft == null || draft.getSeries() == null || draft.getSeries().isEmpty()) {
      throw new BusinessException(ErrorCodes.CURRICULUM_DRAFT_INVALID, "至少需要一个课程系列", 400);
    }
    if (draft.getSeries().size() > MAX_SERIES) {
      throw new BusinessException(ErrorCodes.CURRICULUM_DRAFT_INVALID, "系列数量不能超过 " + MAX_SERIES, 400);
    }
    int total = 0;
    int order = 1;
    for (CurriculumSeriesDraftView series : draft.getSeries()) {
      if (series.getName() == null || series.getName().isBlank()) {
        throw new BusinessException(ErrorCodes.CURRICULUM_DRAFT_INVALID, "系列名称不能为空", 400);
      }
      if (series.getLessons() == null || series.getLessons().isEmpty()) {
        throw new BusinessException(ErrorCodes.CURRICULUM_DRAFT_INVALID, "系列「" + series.getName() + "」至少需要一课", 400);
      }
      if (series.getLessons().size() > MAX_LESSONS_PER_SERIES) {
        throw new BusinessException(ErrorCodes.CURRICULUM_DRAFT_INVALID, "单系列课次不能超过 " + MAX_LESSONS_PER_SERIES, 400);
      }
      series.setSortOrder(order++);
      if (series.getTempId() == null || series.getTempId().isBlank()) {
        series.setTempId("s" + series.getSortOrder());
      }
      int lessonNo = 1;
      for (CurriculumLessonDraftView lesson : series.getLessons()) {
        if (lesson.getTitle() == null || lesson.getTitle().isBlank()) {
          throw new BusinessException(ErrorCodes.CURRICULUM_DRAFT_INVALID, "课标题不能为空", 400);
        }
        lesson.setLessonNo(lessonNo++);
        if (lesson.getSummary() == null) {
          lesson.setSummary("");
        }
        total++;
      }
    }
    if (total > MAX_TOTAL_LESSONS) {
      throw new BusinessException(ErrorCodes.CURRICULUM_DRAFT_INVALID, "主题总课次不能超过 " + MAX_TOTAL_LESSONS, 400);
    }
    if (draft.getSchemaVersion() <= 0) {
      draft.setSchemaVersion(1);
    }
    if (draft.getRouteNarrative() == null) {
      draft.setRouteNarrative("");
    }
    return draft;
  }

  /**
   * 解析课表草案 JSON；失败时返回空结构。
   *
   * @param json 草案 JSON
   * @return 草案视图
   */
  public CurriculumDraftView parseDraft(String json) {
    if (json == null || json.isBlank()) {
      return CurriculumDraftView.builder().schemaVersion(1).routeNarrative("").series(new ArrayList<>()).build();
    }
    try {
      return objectMapper.readValue(json, CurriculumDraftView.class);
    } catch (Exception ex) {
      log.warn("解析课表草案失败: {}", ex.getMessage());
      return CurriculumDraftView.builder().schemaVersion(1).routeNarrative("").series(new ArrayList<>()).build();
    }
  }

  /**
   * 序列化课表草案；Jackson 失败时回退实体工具。
   *
   * @param draft 草案
   * @return JSON 字符串
   */
  public String writeDraft(CurriculumDraftView draft) {
    try {
      return objectMapper.writeValueAsString(draft);
    } catch (Exception ex) {
      return support.writeJson(draft);
    }
  }
}
