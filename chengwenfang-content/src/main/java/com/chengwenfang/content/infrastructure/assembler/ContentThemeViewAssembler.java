package com.chengwenfang.content.infrastructure.assembler;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chengwenfang.common.JacksonJsonSupport;
import com.chengwenfang.content.application.ContentEntityUtil;
import com.chengwenfang.content.application.CourseCurriculumStore;
import com.chengwenfang.content.application.CourseStore;
import com.chengwenfang.content.application.ThemeProfileStore;
import com.chengwenfang.content.domain.entity.ContentModeEntity;
import com.chengwenfang.content.domain.entity.ContentThemeEntity;
import com.chengwenfang.content.domain.entity.CourseSeriesEntity;
import com.chengwenfang.content.facade.constant.ContentModeIds;
import com.chengwenfang.content.facade.dto.view.ContentModeView;
import com.chengwenfang.content.facade.dto.view.ContentThemeView;
import com.chengwenfang.content.facade.dto.view.CurriculumDraftView;
import com.chengwenfang.content.facade.enums.ContentThemeStatus;
import com.chengwenfang.content.infrastructure.mapper.ContentModeMapper;
import com.chengwenfang.content.infrastructure.mapper.ContentThemeMapper;
import com.chengwenfang.content.infrastructure.mapper.CourseSeriesMapper;
import com.chengwenfang.job.facade.JobFacade;
import com.chengwenfang.job.facade.constant.JobTypes;
import com.chengwenfang.job.facade.dto.view.JobView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 教学模式与主题视图组装（含列表批量计数）。
 */
@Component
public class ContentThemeViewAssembler {

  /** 默认课程体系主题 id。 */
  public static final String DEFAULT_THEME_ID = "theme-ai-self-study-zero";
  /** 热点资讯主题 id。 */
  public static final String HOT_NEWS_THEME_ID = "theme-ai-frontier";

  private final CourseStore courseStore;
  private final CourseSeriesMapper courseSeriesMapper;
  private final ContentModeMapper contentModeMapper;
  private final ContentThemeMapper contentThemeMapper;
  private final ThemeProfileStore profileStore;
  private final CourseCurriculumStore curriculumStore;
  private final JobFacade jobs;
  private final JacksonJsonSupport json;

  /**
   * @param courseStore          课程存储
   * @param courseSeriesMapper   系列表
   * @param contentModeMapper    模式表
   * @param contentThemeMapper   主题表
   * @param profileStore         主题画像
   * @param curriculumStore      课表草案
   * @param jobs                 任务门面
   * @param objectMapper         Jackson
   * @param support              内容工具（writeJson 回退）
   */
  public ContentThemeViewAssembler(
      CourseStore courseStore,
      CourseSeriesMapper courseSeriesMapper,
      ContentModeMapper contentModeMapper,
      ContentThemeMapper contentThemeMapper,
      ThemeProfileStore profileStore,
      CourseCurriculumStore curriculumStore,
      JobFacade jobs,
      ObjectMapper objectMapper,
      ContentEntityUtil support) {
    this.courseStore = courseStore;
    this.courseSeriesMapper = courseSeriesMapper;
    this.contentModeMapper = contentModeMapper;
    this.contentThemeMapper = contentThemeMapper;
    this.profileStore = profileStore;
    this.curriculumStore = curriculumStore;
    this.jobs = jobs;
    this.json = new JacksonJsonSupport(objectMapper);
    // support 仅保留构造签名兼容；writeDraft 用 json + support 回退在 writeDraftJson
    this.support = support;
  }

  /** 内容工具（序列化失败回退）。 */
  private final ContentEntityUtil support;

  /**
   * 列出可用教学模式（优先 DB，空表时静态回退）；主题一次查出再按模式分组。
   *
   * @return 模式视图列表
   */
  public List<ContentModeView> listModes() {
    List<ContentModeEntity> modes = contentModeMapper.selectList(new LambdaQueryWrapper<ContentModeEntity>()
        .orderByAsc(ContentModeEntity::getSortOrder)
        .orderByAsc(ContentModeEntity::getCode));
    if (modes.isEmpty()) {
      return staticModesFallback();
    }
    List<String> modeCodes = modes.stream().map(ContentModeEntity::getCode).toList();
    List<ContentThemeEntity> themes = contentThemeMapper.selectList(new LambdaQueryWrapper<ContentThemeEntity>()
        .in(ContentThemeEntity::getModeCode, modeCodes)
        .in(ContentThemeEntity::getStatus,
            ContentThemeStatus.DRAFT.getValue(),
            ContentThemeStatus.READY_FOR_REVIEW.getValue(),
            ContentThemeStatus.ENABLED.getValue())
        .orderByAsc(ContentThemeEntity::getSortOrder)
        .orderByAsc(ContentThemeEntity::getCreatedAt));
    Map<String, ContentModeEntity> modesByCode = modes.stream()
        .collect(Collectors.toMap(ContentModeEntity::getCode, mode -> mode, (a, b) -> a, LinkedHashMap::new));
    Map<String, List<ContentThemeView>> themesByMode = toThemeViewsGrouped(themes, modesByCode);
    List<ContentModeView> result = new ArrayList<>();
    for (ContentModeEntity mode : modes) {
      result.add(ContentModeView.builder()
          .id(mode.getCode())
          .code(mode.getCode())
          .name(mode.getName())
          .description(mode.getDescription())
          .status(mode.getStatus())
          .themes(themesByMode.getOrDefault(mode.getCode(), List.of()))
          .build());
    }
    return result;
  }

  /**
   * 列出模式（过滤已删除主题）；供主题生命周期列表使用。
   *
   * @param modes  模式实体
   * @param themes 已按可见性筛选的主题
   * @return 模式视图
   */
  public List<ContentModeView> toModeViews(List<ContentModeEntity> modes, List<ContentThemeEntity> themes) {
    Map<String, ContentModeEntity> modesByCode = modes.stream()
        .collect(Collectors.toMap(ContentModeEntity::getCode, mode -> mode, (a, b) -> a, LinkedHashMap::new));
    Map<String, List<ContentThemeView>> themesByMode = toThemeViewsGrouped(themes, modesByCode);
    List<ContentModeView> result = new ArrayList<>();
    for (ContentModeEntity mode : modes) {
      result.add(ContentModeView.builder()
          .id(mode.getCode())
          .code(mode.getCode())
          .name(mode.getName())
          .description(mode.getDescription())
          .status(mode.getStatus())
          .themes(themesByMode.getOrDefault(mode.getCode(), List.of()))
          .build());
    }
    return result;
  }

  /**
   * 迁移未执行时的静态模式/主题回退。
   *
   * @return 固定两项模式
   */
  public List<ContentModeView> staticModesFallback() {
    List<ContentModeView> modes = new ArrayList<>();
    modes.add(courseSystemMode());
    modes.add(hotNewsMode());
    return modes;
  }

  /**
   * 按 themeId/code 解析主题；未知时回退课程体系默认主题。
   *
   * @param themeId 主题 id 或 code；空则用默认
   * @return 带 mode 信息的主题视图
   */
  public ContentThemeView resolveTheme(String themeId) {
    String id = themeId == null || themeId.isBlank() ? DEFAULT_THEME_ID : themeId;
    ContentThemeEntity entity = contentThemeMapper.selectById(id);
    if (entity == null) {
      String code = id.replace('-', '_');
      entity = contentThemeMapper.selectOne(new LambdaQueryWrapper<ContentThemeEntity>()
          .eq(ContentThemeEntity::getCode, code)
          .last("LIMIT 1"));
    }
    if (entity == null) {
      String hyphen = id.replace('_', '-');
      for (ContentThemeEntity row : contentThemeMapper.selectList(null)) {
        if (id.equals(row.getId()) || id.equals(row.getCode())
            || codeEquals(row.getCode(), id) || codeEquals(row.getCode(), hyphen)) {
          entity = row;
          break;
        }
      }
    }
    if (entity != null) {
      ContentModeEntity mode = contentModeMapper.selectById(entity.getModeCode());
      return toThemeView(entity, mode);
    }
    for (ContentModeView mode : listModes()) {
      List<ContentThemeView> themes = mode.getThemes() == null ? List.of() : mode.getThemes();
      for (ContentThemeView theme : themes) {
        if (id.equals(theme.getId()) || id.equals(theme.getCode()) || codeEquals(theme.getCode(), id)) {
          return ContentThemeView.builder()
              .id(theme.getId())
              .modeId(mode.getCode())
              .modeName(mode.getName())
              .modeCode(mode.getCode())
              .code(theme.getCode())
              .name(theme.getName())
              .description(theme.getDescription())
              .status(theme.getStatus())
              .seriesCount(theme.getSeriesCount())
              .courseCount(theme.getCourseCount())
              .completedCount(theme.getCompletedCount())
              .itemCount(theme.getItemCount())
              .candidateCount(theme.getCandidateCount())
              .createdVia(theme.getCreatedVia())
              .build();
        }
      }
    }
    ContentThemeView fallback = courseSystemTheme();
    fallback.setModeId(ContentModeIds.COURSE_SYSTEM_CODE);
    fallback.setModeName("课程体系教学模式");
    fallback.setModeCode("course_system");
    return fallback;
  }

  /**
   * 组装单个主题视图（含计数与画像）。
   *
   * @param theme 主题实体
   * @param mode  模式实体；可空
   * @return 主题视图
   */
  public ContentThemeView toThemeView(ContentThemeEntity theme, ContentModeEntity mode) {
    return toThemeView(theme, mode, null);
  }

  /**
   * 组装单个主题视图；可传入批量计数缓存。
   *
   * @param theme   主题实体
   * @param mode    模式实体；可空
   * @param batch   批量计数；null 则现场查询
   * @return 主题视图
   */
  public ContentThemeView toThemeView(
      ContentThemeEntity theme, ContentModeEntity mode, ThemeCountBatch batch) {
    long seriesCount = batch == null
        ? courseSeriesMapper.selectCount(new LambdaQueryWrapper<CourseSeriesEntity>()
            .eq(CourseSeriesEntity::getThemeId, theme.getId()))
        : batch.seriesCount(theme.getId());
    long courseCount = batch == null
        ? courseStore.countByThemeId(theme.getId())
        : batch.courseCount(theme.getId());
    long completedCount = batch == null
        ? courseStore.countCompletedByThemeId(theme.getId())
        : batch.completedCount(theme.getId());
    JobView latestJob = batch == null
        ? jobs.findLatestByThemeAndType(theme.getId(), JobTypes.THEME_CURRICULUM_GENERATION)
        : batch.latestJob(theme.getId());
    CurriculumDraftView draft = curriculumStore.loadDraft(theme.getId());
    ContentThemeView.ContentThemeViewBuilder builder = ContentThemeView.builder()
        .id(theme.getId())
        .modeId(theme.getModeCode())
        .code(theme.getCode())
        .name(theme.getName())
        .description(theme.getDescription())
        .status(theme.getStatus())
        .seriesCount((int) seriesCount)
        .courseCount((int) courseCount)
        .completedCount((int) completedCount)
        .createdVia(theme.getCreatedVia())
        .briefingJson(profileStore.loadBriefingJson(theme.getId()))
        .curriculumDraftJson(writeDraftJson(draft))
        .routeNarrative(curriculumStore.loadRouteNarrative(theme.getId()))
        .defaultsJson(profileStore.loadDefaultsJson(theme.getId()))
        .generateJobId(latestJob == null ? null : latestJob.getId())
        .deletedAt(theme.getDeletedAt());
    if (mode != null) {
      builder.modeName(mode.getName()).modeCode(mode.getCode());
    }
    if (HOT_NEWS_THEME_ID.equals(theme.getId())) {
      builder.itemCount(0).candidateCount(0);
    }
    return builder.build();
  }

  /**
   * 构造「课程体系教学模式」静态定义。
   *
   * @return 模式视图
   */
  public ContentModeView courseSystemMode() {
    return ContentModeView.builder()
        .id(ContentModeIds.COURSE_SYSTEM_CODE)
        .code(ContentModeIds.COURSE_SYSTEM_CODE)
        .name("课程体系教学模式")
        .description("围绕主题规划、生成和维护系列课程内容。")
        .status("enabled")
        .themes(List.of(courseSystemTheme()))
        .build();
  }

  /**
   * 构造默认课程主题，并统计系列/课程/已完成数。
   *
   * @return 主题视图
   */
  public ContentThemeView courseSystemTheme() {
    long seriesCount = courseSeriesMapper.selectCount(new LambdaQueryWrapper<CourseSeriesEntity>()
        .eq(CourseSeriesEntity::getThemeId, DEFAULT_THEME_ID));
    long courseCount = courseStore.countByThemeId(DEFAULT_THEME_ID);
    long completedCount = courseStore.countCompletedByThemeId(DEFAULT_THEME_ID);
    return ContentThemeView.builder()
        .id(DEFAULT_THEME_ID)
        .code("ai_self_study_zero")
        .name("AI 自学零基础")
        .description("从零开始系统学习 AI。")
        .status("enabled")
        .seriesCount((int) seriesCount)
        .courseCount((int) courseCount)
        .completedCount((int) completedCount)
        .createdVia("seed")
        .build();
  }

  /**
   * 构造「热点资讯模式」静态定义（主题计数占位为 0）。
   *
   * @return 模式视图
   */
  public ContentModeView hotNewsMode() {
    ContentThemeView theme = ContentThemeView.builder()
        .id(HOT_NEWS_THEME_ID)
        .code("ai_frontier")
        .name("AI 前沿热点")
        .description("跟踪并沉淀值得写作的 AI 资讯选题。")
        .status("enabled")
        .seriesCount(0)
        .courseCount(0)
        .completedCount(0)
        .itemCount(0)
        .candidateCount(0)
        .build();
    return ContentModeView.builder()
        .id(ContentModeIds.HOT_NEWS_CODE)
        .code(ContentModeIds.HOT_NEWS_CODE)
        .name("热点资讯模式")
        .description("从多来源发现热点，沉淀为值得深入写作的选题。")
        .status("enabled")
        .themes(List.of(theme))
        .build();
  }

  /**
   * 批量加载系列/课程/完成数与最近课表任务。
   *
   * @param themeIds 主题 id 集合
   * @return 计数批次
   */
  public ThemeCountBatch loadCountBatch(Set<String> themeIds) {
    Map<String, Long> seriesCounts = new HashMap<>();
    Map<String, Long> courseCounts = new HashMap<>();
    Map<String, Long> completedCounts = new HashMap<>();
    Map<String, JobView> latestJobs = new HashMap<>();
    if (CollUtil.isEmpty(themeIds)) {
      return new ThemeCountBatch(seriesCounts, courseCounts, completedCounts, latestJobs);
    }
    List<CourseSeriesEntity> seriesRows = courseSeriesMapper.selectList(new LambdaQueryWrapper<CourseSeriesEntity>()
        .in(CourseSeriesEntity::getThemeId, themeIds));
    for (CourseSeriesEntity row : seriesRows) {
      seriesCounts.merge(row.getThemeId(), 1L, Long::sum);
    }
    courseCounts.putAll(courseStore.countByThemeIds(themeIds));
    completedCounts.putAll(courseStore.countCompletedByThemeIds(themeIds));
    latestJobs.putAll(jobs.findLatestByThemesAndType(themeIds, JobTypes.THEME_CURRICULUM_GENERATION));
    return new ThemeCountBatch(seriesCounts, courseCounts, completedCounts, latestJobs);
  }

  /**
   * 按模式编码分组组装主题视图。
   *
   * @param themes      主题实体列表
   * @param modesByCode 模式 code → 实体
   * @return 模式 code → 主题视图列表
   */
  private Map<String, List<ContentThemeView>> toThemeViewsGrouped(
      List<ContentThemeEntity> themes, Map<String, ContentModeEntity> modesByCode) {
    Map<String, List<ContentThemeView>> grouped = new LinkedHashMap<>();
    if (CollUtil.isEmpty(themes)) {
      return grouped;
    }
    Set<String> themeIds = themes.stream().map(ContentThemeEntity::getId).collect(Collectors.toSet());
    // 系列计数一次查出；课程完成数与任务仍按主题（画像/草案结构深，保持原读路径）
    ThemeCountBatch batch = loadCountBatch(themeIds);
    for (ContentThemeEntity theme : themes) {
      ContentModeEntity mode = modesByCode.get(theme.getModeCode());
      grouped.computeIfAbsent(theme.getModeCode(), key -> new ArrayList<>())
          .add(toThemeView(theme, mode, batch));
    }
    return grouped;
  }

  /**
   * 序列化课表草案；Jackson 失败时回退门面写 JSON。
   *
   * @param draft 课表草案
   * @return JSON 文本
   */
  private String writeDraftJson(CurriculumDraftView draft) {
    try {
      return json.objectMapper().writeValueAsString(draft);
    } catch (Exception ignored) {
      return support.writeJson(draft);
    }
  }

  /**
   * 比较模式/渠道等编码；兼容下划线与短横线写法。
   *
   * @param code 规范编码
   * @param raw  原始编码
   * @return 相等则为 true
   */
  private static boolean codeEquals(String code, String raw) {
    if (code == null || raw == null) {
      return false;
    }
    return code.equals(raw) || code.replace('_', '-').equals(raw.replace('_', '-'));
  }

  /**
   * 主题列表计数缓存。
   *
   * @param seriesCounts    系列数
   * @param courseCounts    课程数
   * @param completedCounts 已完成数
   * @param latestJobs      最近课表生成任务
   */
  public record ThemeCountBatch(
      Map<String, Long> seriesCounts,
      Map<String, Long> courseCounts,
      Map<String, Long> completedCounts,
      Map<String, JobView> latestJobs) {

    long seriesCount(String themeId) {
      return seriesCounts.getOrDefault(themeId, 0L);
    }

    long courseCount(String themeId) {
      return courseCounts.getOrDefault(themeId, 0L);
    }

    long completedCount(String themeId) {
      return completedCounts.getOrDefault(themeId, 0L);
    }

    JobView latestJob(String themeId) {
      return latestJobs.get(themeId);
    }
  }
}
