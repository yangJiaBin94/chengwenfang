package com.workbench.content.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.common.IdsUtil;
import com.workbench.content.domain.entity.CourseCurriculumDraftLessonEntity;
import com.workbench.content.domain.entity.CourseCurriculumDraftOutcomeEntity;
import com.workbench.content.domain.entity.CourseCurriculumDraftSeriesEntity;
import com.workbench.content.domain.entity.CourseCurriculumEntity;
import com.workbench.content.facade.dto.view.CurriculumDraftView;
import com.workbench.content.facade.dto.view.CurriculumLessonDraftView;
import com.workbench.content.facade.dto.view.CurriculumSeriesDraftView;
import com.workbench.content.infrastructure.mapper.CourseCurriculumDraftLessonMapper;
import com.workbench.content.infrastructure.mapper.CourseCurriculumDraftOutcomeMapper;
import com.workbench.content.infrastructure.mapper.CourseCurriculumDraftSeriesMapper;
import com.workbench.content.infrastructure.mapper.CourseCurriculumMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 课程主题课表（路线叙事与草案）结构化读写。
 */
@Service
public class CourseCurriculumStore {
  private final CourseCurriculumMapper curriculumMapper;
  private final CourseCurriculumDraftSeriesMapper seriesMapper;
  private final CourseCurriculumDraftLessonMapper lessonMapper;
  private final CourseCurriculumDraftOutcomeMapper outcomeMapper;

  public CourseCurriculumStore(
      CourseCurriculumMapper curriculumMapper,
      CourseCurriculumDraftSeriesMapper seriesMapper,
      CourseCurriculumDraftLessonMapper lessonMapper,
      CourseCurriculumDraftOutcomeMapper outcomeMapper) {
    this.curriculumMapper = curriculumMapper;
    this.seriesMapper = seriesMapper;
    this.lessonMapper = lessonMapper;
    this.outcomeMapper = outcomeMapper;
  }

  /**
   * 读取课表草案；无则空结构。
   *
   * @param themeId 主题 id
   * @return 草案视图
   */
  public CurriculumDraftView loadDraft(String themeId) {
    CourseCurriculumEntity header = curriculumMapper.selectById(themeId);
    if (header == null || header.getDraftSchemaVersion() == null) {
      return emptyDraft();
    }
    List<CourseCurriculumDraftSeriesEntity> seriesRows = seriesMapper.selectList(
        new LambdaQueryWrapper<CourseCurriculumDraftSeriesEntity>()
            .eq(CourseCurriculumDraftSeriesEntity::getThemeId, themeId)
            .orderByAsc(CourseCurriculumDraftSeriesEntity::getSeq));
    List<CurriculumSeriesDraftView> seriesViews = new ArrayList<>();
    for (CourseCurriculumDraftSeriesEntity seriesRow : seriesRows) {
      List<CourseCurriculumDraftLessonEntity> lessonRows = lessonMapper.selectList(
          new LambdaQueryWrapper<CourseCurriculumDraftLessonEntity>()
              .eq(CourseCurriculumDraftLessonEntity::getDraftSeriesId, seriesRow.getId())
              .orderByAsc(CourseCurriculumDraftLessonEntity::getSeq));
      List<CurriculumLessonDraftView> lessons = new ArrayList<>();
      for (CourseCurriculumDraftLessonEntity lessonRow : lessonRows) {
        List<CourseCurriculumDraftOutcomeEntity> outcomes = outcomeMapper.selectList(
            new LambdaQueryWrapper<CourseCurriculumDraftOutcomeEntity>()
                .eq(CourseCurriculumDraftOutcomeEntity::getDraftLessonId, lessonRow.getId())
                .orderByAsc(CourseCurriculumDraftOutcomeEntity::getSeq));
        lessons.add(CurriculumLessonDraftView.builder()
            .lessonNo(lessonRow.getLessonNo())
            .title(lessonRow.getTitle())
            .summary(StrUtil.nullToEmpty(lessonRow.getSummary()))
            .learningOutcomes(outcomes.stream().map(CourseCurriculumDraftOutcomeEntity::getContent).toList())
            .build());
      }
      seriesViews.add(CurriculumSeriesDraftView.builder()
          .tempId(seriesRow.getTempId())
          .name(seriesRow.getName())
          .sortOrder(seriesRow.getSortOrder())
          .positioning(seriesRow.getPositioning())
          .lessons(lessons)
          .build());
    }
    return CurriculumDraftView.builder()
        .schemaVersion(header.getDraftSchemaVersion() == null ? 1 : header.getDraftSchemaVersion())
        .routeNarrative(StrUtil.nullToEmpty(header.getDraftRouteNarrative()))
        .series(seriesViews)
        .build();
  }

  /**
   * 保存课表草案（替换系列树）。
   *
   * @param themeId   主题 id
   * @param draft     草案
   * @param updatedAt 更新时间
   */
  @Transactional
  public void saveDraft(String themeId, CurriculumDraftView draft, LocalDateTime updatedAt) {
    ensureHeader(themeId, updatedAt);
    CurriculumDraftView body = draft == null ? emptyDraft() : draft;
    CourseCurriculumEntity header = curriculumMapper.selectById(themeId);
    header.setDraftSchemaVersion(body.getSchemaVersion() <= 0 ? 1 : body.getSchemaVersion());
    header.setDraftRouteNarrative(StrUtil.nullToEmpty(body.getRouteNarrative()));
    header.setUpdatedAt(updatedAt);
    curriculumMapper.updateById(header);
    clearDraftTree(themeId);
    if (CollUtil.isEmpty(body.getSeries())) {
      return;
    }
    int seriesSeq = 0;
    for (CurriculumSeriesDraftView series : body.getSeries()) {
      seriesSeq++;
      String seriesId = IdsUtil.newId();
      seriesMapper.insert(CourseCurriculumDraftSeriesEntity.builder()
          .id(seriesId)
          .themeId(themeId)
          .seq(seriesSeq)
          .tempId(series.getTempId())
          .name(series.getName())
          .sortOrder(series.getSortOrder())
          .positioning(series.getPositioning())
          .build());
      if (CollUtil.isEmpty(series.getLessons())) {
        continue;
      }
      int lessonSeq = 0;
      for (CurriculumLessonDraftView lesson : series.getLessons()) {
        lessonSeq++;
        String lessonId = IdsUtil.newId();
        lessonMapper.insert(CourseCurriculumDraftLessonEntity.builder()
            .id(lessonId)
            .draftSeriesId(seriesId)
            .seq(lessonSeq)
            .lessonNo(lesson.getLessonNo())
            .title(lesson.getTitle())
            .summary(lesson.getSummary())
            .build());
        if (CollUtil.isEmpty(lesson.getLearningOutcomes())) {
          continue;
        }
        int outcomeSeq = 0;
        for (String outcome : lesson.getLearningOutcomes()) {
          if (StrUtil.isBlank(outcome)) {
            continue;
          }
          outcomeSeq++;
          outcomeMapper.insert(CourseCurriculumDraftOutcomeEntity.builder()
              .id(IdsUtil.newId())
              .draftLessonId(lessonId)
              .seq(outcomeSeq)
              .content(outcome.trim())
              .build());
        }
      }
    }
  }

  /**
   * 读取已提交路线叙事。
   *
   * @param themeId 主题 id
   * @return 叙事；无则空串
   */
  public String loadRouteNarrative(String themeId) {
    CourseCurriculumEntity header = curriculumMapper.selectById(themeId);
    return header == null ? "" : StrUtil.nullToEmpty(header.getRouteNarrative());
  }

  /**
   * 保存已提交路线叙事。
   *
   * @param themeId   主题 id
   * @param narrative 叙事
   * @param updatedAt 更新时间
   */
  @Transactional
  public void saveRouteNarrative(String themeId, String narrative, LocalDateTime updatedAt) {
    ensureHeader(themeId, updatedAt);
    CourseCurriculumEntity header = curriculumMapper.selectById(themeId);
    header.setRouteNarrative(narrative);
    header.setUpdatedAt(updatedAt);
    curriculumMapper.updateById(header);
  }

  private void ensureHeader(String themeId, LocalDateTime updatedAt) {
    if (curriculumMapper.selectById(themeId) != null) {
      return;
    }
    curriculumMapper.insert(CourseCurriculumEntity.builder()
        .themeId(themeId)
        .updatedAt(updatedAt)
        .build());
  }

  private void clearDraftTree(String themeId) {
    List<CourseCurriculumDraftSeriesEntity> seriesRows = seriesMapper.selectList(
        new LambdaQueryWrapper<CourseCurriculumDraftSeriesEntity>()
            .eq(CourseCurriculumDraftSeriesEntity::getThemeId, themeId));
    for (CourseCurriculumDraftSeriesEntity seriesRow : seriesRows) {
      List<CourseCurriculumDraftLessonEntity> lessons = lessonMapper.selectList(
          new LambdaQueryWrapper<CourseCurriculumDraftLessonEntity>()
              .eq(CourseCurriculumDraftLessonEntity::getDraftSeriesId, seriesRow.getId()));
      for (CourseCurriculumDraftLessonEntity lesson : lessons) {
        outcomeMapper.delete(new LambdaQueryWrapper<CourseCurriculumDraftOutcomeEntity>()
            .eq(CourseCurriculumDraftOutcomeEntity::getDraftLessonId, lesson.getId()));
      }
      lessonMapper.delete(new LambdaQueryWrapper<CourseCurriculumDraftLessonEntity>()
          .eq(CourseCurriculumDraftLessonEntity::getDraftSeriesId, seriesRow.getId()));
    }
    seriesMapper.delete(new LambdaQueryWrapper<CourseCurriculumDraftSeriesEntity>()
        .eq(CourseCurriculumDraftSeriesEntity::getThemeId, themeId));
  }

  private static CurriculumDraftView emptyDraft() {
    return CurriculumDraftView.builder()
        .schemaVersion(1)
        .routeNarrative("")
        .series(new ArrayList<>())
        .build();
  }
}
