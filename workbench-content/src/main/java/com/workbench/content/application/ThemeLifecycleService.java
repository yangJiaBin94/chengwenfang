package com.workbench.content.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.workbench.common.BusinessException;
import com.workbench.common.ErrorCodes;
import com.workbench.common.IdsUtil;
import com.workbench.content.domain.entity.ArticleEntity;
import com.workbench.content.domain.entity.ContentModeEntity;
import com.workbench.content.domain.entity.ContentThemeEntity;
import com.workbench.content.domain.entity.CourseEntity;
import com.workbench.content.domain.entity.CourseSeriesEntity;
import com.workbench.content.facade.constant.ContentModeIds;
import com.workbench.content.facade.dto.request.ThemeDraftCreateRequest;
import com.workbench.content.facade.dto.request.ThemePatchRequest;
import com.workbench.content.facade.dto.view.ContentModeView;
import com.workbench.content.facade.dto.view.ContentThemeView;
import com.workbench.content.facade.dto.view.CourseSeriesView;
import com.workbench.content.facade.dto.view.CourseView;
import com.workbench.content.facade.dto.view.ThemeRouteSeriesView;
import com.workbench.content.facade.dto.view.ThemeRouteView;
import com.workbench.content.facade.enums.ContentThemeCreatedVia;
import com.workbench.content.facade.enums.ContentThemeStatus;
import com.workbench.content.infrastructure.assembler.ContentThemeViewAssembler;
import com.workbench.content.infrastructure.assembler.CourseViewAssembler;
import com.workbench.content.infrastructure.mapper.ContentModeMapper;
import com.workbench.content.infrastructure.mapper.ContentThemeMapper;
import com.workbench.content.infrastructure.mapper.CourseSeriesMapper;
import com.workbench.job.facade.JobFacade;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 内容主题生命周期：列表、草稿创建、元数据补丁、假删/恢复、启停与路线查询。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThemeLifecycleService {
  /** 主题编码正则：小写字母开头的 snake_case。 */
  private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,62}$");

  /** 模式表。 */
  private final ContentModeMapper modeMapper;
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
  /** 任务门面。 */
  private final JobFacade jobs;
  /** 实体与 JSON 工具。 */
  private final ContentEntityUtil support;
  /** 视图映射。 */
  private final ContentViewMapper views;
  /** 主题视图组装。 */
  private final ContentThemeViewAssembler themeViews;
  /** 主题可见性守卫。 */
  private final ThemeVisibilityGuard visibility;

  /**
   * 列出模式（DB）；课程体系主题来自 content_themes。
   *
   * @return 模式列表
   */
  public List<ContentModeView> listModes() {
    List<ContentModeEntity> modes = modeMapper.selectList(new LambdaQueryWrapper<ContentModeEntity>()
        .orderByAsc(ContentModeEntity::getSortOrder)
        .orderByAsc(ContentModeEntity::getCode));
    if (modes.isEmpty()) {
      // 迁移未跑时回退静态定义，避免前端空白
      return views.staticModesFallback();
    }
    List<String> modeCodes = modes.stream().map(ContentModeEntity::getCode).toList();
    // 一次查出全部可见主题，再按模式分组组装（计数批量）
    List<ContentThemeEntity> visible = themeMapper.selectList(new LambdaQueryWrapper<ContentThemeEntity>()
        .in(ContentThemeEntity::getModeCode, modeCodes)
        .isNull(ContentThemeEntity::getDeletedAt)
        .in(ContentThemeEntity::getStatus,
            ContentThemeStatus.DRAFT.getValue(),
            ContentThemeStatus.READY_FOR_REVIEW.getValue(),
            ContentThemeStatus.ENABLED.getValue())
        .orderByAsc(ContentThemeEntity::getSortOrder)
        .orderByAsc(ContentThemeEntity::getCreatedAt));
    return themeViews.toModeViews(modes, visible);
  }

  /**
   * 列出模式下属未删除主题（含 disabled，供管理折叠区）。
   *
   * @param modeId 模式 id 或 code
   * @return 主题列表
   */
  public List<ContentThemeView> listThemes(String modeId) {
    return listThemes(modeId, false);
  }

  /**
   * 列出模式下属主题。
   *
   * @param modeId         模式 id 或 code
   * @param includeDeleted true 时仅返回已软删主题；false 排除已删
   * @return 主题列表
   */
  public List<ContentThemeView> listThemes(String modeId, boolean includeDeleted) {
    ContentModeEntity mode = requireMode(modeId);
    LambdaQueryWrapper<ContentThemeEntity> query = new LambdaQueryWrapper<ContentThemeEntity>()
        .eq(ContentThemeEntity::getModeCode, mode.getCode())
        .orderByAsc(ContentThemeEntity::getSortOrder)
        .orderByAsc(ContentThemeEntity::getCreatedAt);
    if (includeDeleted) {
      query.isNotNull(ContentThemeEntity::getDeletedAt);
    } else {
      query.isNull(ContentThemeEntity::getDeletedAt);
    }
    return themeMapper.selectList(query).stream().map(theme -> toThemeView(theme, mode)).toList();
  }

  /**
   * 主题详情。
   *
   * @param themeId 主题 id 或 code
   * @return 主题视图
   */
  public ContentThemeView getTheme(String themeId) {
    ContentThemeEntity theme = requireTheme(themeId);
    ContentModeEntity mode = modeMapper.selectById(theme.getModeCode());
    return toThemeView(theme, mode);
  }

  /**
   * 确保内置种子主题存在（固定 id/code，created_via=seed）；已存在则直接返回。
   *
   * @param themeId     固定主题 id
   * @param modeId      模式 id/code
   * @param code        主题 code
   * @param name        名称
   * @param description 描述
   * @param sortOrder   排序
   * @return 主题视图
   */
  @Transactional
  public ContentThemeView ensureSeedTheme(
      String themeId,
      String modeId,
      String code,
      String name,
      String description,
      int sortOrder) {
    ContentThemeEntity existing = themeMapper.selectById(themeId);
    if (existing != null && existing.getDeletedAt() == null) {
      ContentModeEntity mode = modeMapper.selectById(existing.getModeCode());
      return toThemeView(existing, mode);
    }
    ContentModeEntity mode = requireMode(modeId);
    String normalizedCode = normalizeCode(code);
    ensureCodeUnique(mode.getCode(), normalizedCode, themeId);
    LocalDateTime now = LocalDateTime.now();
    Map<String, Object> briefing = new LinkedHashMap<>();
    briefing.put("schema_version", 1);
    briefing.put("step", 1);
    Map<String, Object> naming = new LinkedHashMap<>();
    naming.put("name", name);
    naming.put("description", StrUtil.nullToEmpty(description));
    naming.put("code", normalizedCode);
    briefing.put("naming", naming);
    ContentThemeEntity theme = ContentThemeEntity.builder()
        .id(themeId)
        .modeCode(mode.getCode())
        .code(normalizedCode)
        .name(name)
        .description(StrUtil.nullToEmpty(description))
        .status(ContentThemeStatus.DRAFT.getValue())
        .sortOrder(sortOrder)
        .createdVia(ContentThemeCreatedVia.SEED.getValue())
        .createdAt(now)
        .updatedAt(now)
        .build();
    themeMapper.insert(theme);
    profileStore.saveBriefing(theme.getId(), briefing, now);
    curriculumStore.saveRouteNarrative(theme.getId(), null, now);
    log.info("已创建种子主题 id={} code={}", theme.getId(), theme.getCode());
    return toThemeView(theme, mode);
  }

  /**
   * 创建向导草稿主题。
   *
   * @param modeId  模式 id/code（课程体系或热点资讯）
   * @param request 可选初始字段
   * @return 新建主题视图
   */
  @Transactional
  public ContentThemeView createDraft(String modeId, ThemeDraftCreateRequest request) {
    ContentModeEntity mode = requireMode(modeId);
    if (!ContentModeIds.isCourseSystem(mode.getCode(), mode.getCode())
        && !ContentModeIds.isHotNews(mode.getCode(), mode.getCode())) {
      throw new BusinessException(ErrorCodes.THEME_INVALID_STATUS, "当前模式不支持提问式创建主题", 400);
    }
    ThemeDraftCreateRequest body = request == null ? new ThemeDraftCreateRequest() : request;
    LocalDateTime now = LocalDateTime.now();
    String name = StrUtil.blankToDefault(StrUtil.trim(StrUtil.nullToEmpty(body.getName())), "未命名主题");
    // 自动生成编码时若冲突则追加后缀，避免向导保存直接失败
    String code = allocateUniqueCode(
        mode.getCode(),
        normalizeCode(StrUtil.blankToDefault(
            StrUtil.trim(StrUtil.nullToEmpty(body.getCode())), slugFromName(name))));

    Map<String, Object> briefing = new LinkedHashMap<>();
    briefing.put("schema_version", 1);
    briefing.put("step", 1);
    if (body.getBriefing() != null) {
      briefing.putAll(body.getBriefing().mutableMap());
    }
    Map<String, Object> naming = new LinkedHashMap<>();
    naming.put("name", name);
    naming.put(
        "description",
        StrUtil.blankToDefault(StrUtil.trim(StrUtil.nullToEmpty(body.getDescription())), ""));
    naming.put("code", code);
    briefing.put("naming", naming);

    ContentThemeEntity theme = ContentThemeEntity.builder()
        .id("theme-" + IdsUtil.newId().replace("-", "").substring(0, 16))
        .modeCode(mode.getCode())
        .code(code)
        .name(name)
        .description(StrUtil.blankToDefault(StrUtil.trim(StrUtil.nullToEmpty(body.getDescription())), ""))
        .status(ContentThemeStatus.DRAFT.getValue())
        .sortOrder(nextSortOrder(mode.getCode()))
        .createdVia(ContentThemeCreatedVia.WIZARD.getValue())
        .createdAt(now)
        .updatedAt(now)
        .build();
    themeMapper.insert(theme);
    // 问卷写入画像表；课表头占位
    profileStore.saveBriefing(theme.getId(), briefing, now);
    curriculumStore.saveRouteNarrative(theme.getId(), null, now);
    log.info("创建主题草稿 id={} code={}", theme.getId(), theme.getCode());
    return toThemeView(theme, mode);
  }

  /**
   * 补丁主题元数据或停用/启用。
   *
   * @param themeId 主题 id/code
   * @param request 补丁
   * @return 更新后主题
   */
  @Transactional
  public ContentThemeView patchTheme(String themeId, ThemePatchRequest request) {
    ContentThemeEntity theme = requireActiveTheme(themeId);
    ThemePatchRequest body = request == null ? new ThemePatchRequest() : request;
    if (body.getName() != null && !body.getName().isBlank()) {
      theme.setName(body.getName().trim());
    }
    if (body.getDescription() != null) {
      theme.setDescription(body.getDescription().trim());
    }
    if (body.getSortOrder() != null) {
      theme.setSortOrder(body.getSortOrder());
    }
    if (body.getStatus() != null && !body.getStatus().isBlank()) {
      ContentThemeStatus next = ContentThemeStatus.from(body.getStatus());
      if (next != ContentThemeStatus.ENABLED && next != ContentThemeStatus.DISABLED) {
        throw new BusinessException(ErrorCodes.THEME_INVALID_STATUS, "状态仅支持 enabled 或 disabled", 400);
      }
      if (!ContentThemeStatus.ENABLED.getValue().equals(theme.getStatus())
          && !ContentThemeStatus.DISABLED.getValue().equals(theme.getStatus())) {
        throw new BusinessException(ErrorCodes.THEME_INVALID_STATUS, "未确认主题不能停用/启用，请先完成主题确认", 400);
      }
      theme.setStatus(next.getValue());
    }
    theme.setUpdatedAt(LocalDateTime.now());
    themeMapper.updateById(theme);
    return getTheme(theme.getId());
  }

  /**
   * 假删主题：只写 deleted_at，不物理删除系列/课程/正文。
   *
   * @param themeId 主题 id/code
   */
  @Transactional
  public void deleteTheme(String themeId) {
    ContentThemeEntity theme = requireTheme(themeId);
    if (theme.getDeletedAt() != null) {
      return;
    }
    if (ContentThemeCreatedVia.SEED.matches(theme.getCreatedVia())) {
      throw new BusinessException(ErrorCodes.THEME_DELETE_FORBIDDEN, "内置种子主题不可删除，可停用", 400);
    }
    // 进行中的课表生成/抓取等任务未结束时禁止删除，避免下游写回已删主题
    if (jobs.hasActiveJobsForTheme(theme.getId())) {
      throw new BusinessException(
          ErrorCodes.THEME_DELETE_FORBIDDEN, "主题仍有进行中的任务，请完成或取消后再删除", 400);
    }
    LocalDateTime now = LocalDateTime.now();
    theme.setDeletedAt(now);
    theme.setStatus(ContentThemeStatus.DISABLED.getValue());
    theme.setUpdatedAt(now);
    themeMapper.updateById(theme);
    log.info("已假删主题 id={} code={}", theme.getId(), theme.getCode());
  }

  /**
   * 恢复已假删主题，清空 deleted_at 并置为停用。
   *
   * @param themeId 主题 id/code
   * @return 恢复后的主题视图
   */
  @Transactional
  public ContentThemeView restoreTheme(String themeId) {
    ContentThemeEntity theme = requireTheme(themeId);
    LocalDateTime now = LocalDateTime.now();
    themeMapper.update(null, new LambdaUpdateWrapper<ContentThemeEntity>()
        .eq(ContentThemeEntity::getId, theme.getId())
        .set(ContentThemeEntity::getDeletedAt, null)
        .set(ContentThemeEntity::getStatus, ContentThemeStatus.DISABLED.getValue())
        .set(ContentThemeEntity::getUpdatedAt, now));
    log.info("已恢复主题 id={} code={}", theme.getId(), theme.getCode());
    return getTheme(theme.getId());
  }

  /**
   * 主题路线（已落库系列+课）。
   *
   * @param themeId 主题 id/code
   * @return 路线视图
   */
  public ThemeRouteView getRoute(String themeId) {
    ContentThemeEntity theme = requireActiveTheme(themeId);
    List<CourseSeriesEntity> seriesList = courseSeriesMapper.selectList(new LambdaQueryWrapper<CourseSeriesEntity>()
        .eq(CourseSeriesEntity::getThemeId, theme.getId())
        .orderByAsc(CourseSeriesEntity::getSortOrder));
    Map<String, CourseSeriesEntity> seriesById = seriesList.stream()
        .collect(Collectors.toMap(CourseSeriesEntity::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    List<ThemeRouteSeriesView> blocks = new ArrayList<>();
    Map<String, ArticleEntity> articlesById = new HashMap<>();
    Map<String, List<CourseEntity>> lessonsBySeriesId = new LinkedHashMap<>();
    List<CourseEntity> allLessons = new ArrayList<>();
    for (CourseSeriesEntity series : seriesList) {
      List<CourseEntity> lessons = courseStore.listBySeriesId(series.getId());
      lessonsBySeriesId.put(series.getId(), lessons);
      allLessons.addAll(lessons);
      for (CourseEntity lesson : lessons) {
        indexArticlePointer(articlesById, lesson.getCurrentDraftDocumentId());
        indexArticlePointer(articlesById, lesson.getCurrentWechatDocumentId());
      }
    }
    CourseViewAssembler.CourseMediaBatch mediaBatch = views.courseAssembler().loadMediaBatch(
        CourseViewAssembler.CourseMediaBatch.workIdsOf(allLessons));
    for (CourseSeriesEntity series : seriesList) {
      List<CourseEntity> lessons = lessonsBySeriesId.getOrDefault(series.getId(), List.of());
      List<CourseView> lessonViews = lessons.stream()
          .map(c -> views.courseView(
              c, seriesById, articlesById, mediaBatch.mediaByWorkId(), mediaBatch.filesById()))
          .toList();
      String positioning = "";
      if (!lessons.isEmpty() && StrUtil.isNotBlank(lessons.get(0).getSeriesPositioning())) {
        positioning = lessons.get(0).getSeriesPositioning();
      }
      blocks.add(ThemeRouteSeriesView.builder()
          .id(series.getId())
          .name(series.getName())
          .sortOrder(series.getSortOrder())
          .positioning(positioning)
          .lessons(lessonViews)
          .build());
    }
    return ThemeRouteView.builder()
        .themeId(theme.getId())
        .themeCode(theme.getCode())
        .themeName(theme.getName())
        .routeNarrative(curriculumStore.loadRouteNarrative(theme.getId()))
        .series(blocks)
        .build();
  }

  /**
   * 主题下系列列表。
   *
   * @param themeId 主题 id/code
   * @return 系列视图
   */
  public List<CourseSeriesView> listSeries(String themeId) {
    ContentThemeEntity theme = requireActiveTheme(themeId);
    List<CourseSeriesEntity> seriesRows = courseSeriesMapper.selectList(new LambdaQueryWrapper<CourseSeriesEntity>()
        .eq(CourseSeriesEntity::getThemeId, theme.getId())
        .orderByAsc(CourseSeriesEntity::getSortOrder));
    List<CourseSeriesView> result = new ArrayList<>();
    for (CourseSeriesEntity s : seriesRows) {
      long count = courseStore.countBySeriesId(s.getId());
      result.add(CourseSeriesView.builder()
          .id(s.getId())
          .themeId(s.getThemeId())
          .topicId(null)
          .name(s.getName())
          .sortOrder(s.getSortOrder())
          .courseCount((int) count)
          .build());
    }
    return result;
  }

  /**
   * 解析未删除的主题；已软删则 404。
   *
   * @param themeId id 或 code
   * @return 实体
   */
  public ContentThemeEntity requireActiveTheme(String themeId) {
    ContentThemeEntity theme = requireTheme(themeId);
    visibility.assertThemeActive(theme);
    return theme;
  }

  /**
   * 解析已启用主题；未启用或已停用时拒绝写操作。
   *
   * @param themeId id 或 code
   * @return 实体
   */
  public ContentThemeEntity requireEnabledTheme(String themeId) {
    ContentThemeEntity theme = requireActiveTheme(themeId);
    if (!ContentThemeStatus.ENABLED.getValue().equals(theme.getStatus())) {
      throw new BusinessException(
          ErrorCodes.THEME_INVALID_STATUS, "主题未启用或已停用，无法执行此操作", 400);
    }
    return theme;
  }

  /**
   * 解析主题实体（含已软删，供恢复与元数据）。
   *
   * @param themeId id 或 code
   * @return 实体
   */
  public ContentThemeEntity requireTheme(String themeId) {
    if (themeId == null || themeId.isBlank()) {
      throw new BusinessException(ErrorCodes.THEME_NOT_FOUND, "主题不存在", 404);
    }
    ContentThemeEntity byId = themeMapper.selectById(themeId);
    if (byId != null) {
      return byId;
    }
    // code 或 slug（下划线/连字符）
    String code = themeId.replace('-', '_');
    ContentThemeEntity byCode = themeMapper.selectOne(new LambdaQueryWrapper<ContentThemeEntity>()
        .eq(ContentThemeEntity::getCode, code)
        .last("LIMIT 1"));
    if (byCode != null) {
      return byCode;
    }
    String hyphenCode = themeId.replace('_', '-');
    List<ContentThemeEntity> all = themeMapper.selectList(null);
    for (ContentThemeEntity t : all) {
      if (themeId.equals(t.getCode()) || themeId.equals(t.getId())
          || hyphenCode.equals(t.getCode().replace('_', '-'))
          || code.equals(t.getCode())) {
        return t;
      }
    }
    throw new BusinessException(ErrorCodes.THEME_NOT_FOUND, "主题不存在: " + themeId, 404);
  }

  /**
   * 解析模式实体。
   *
   * @param modeId 模式 id 或 code
   * @return 实体
   */
  public ContentModeEntity requireMode(String modeId) {
    if (modeId == null || modeId.isBlank()) {
      throw new BusinessException(ErrorCodes.MODE_NOT_FOUND, "模式不存在", 404);
    }
    String code = ContentModeIds.resolveCode(modeId);
    ContentModeEntity byCode = modeMapper.selectById(code);
    if (byCode != null) {
      return byCode;
    }
    throw new BusinessException(ErrorCodes.MODE_NOT_FOUND, "模式不存在: " + modeId, 404);
  }

  /**
   * 已确认主题不可再改问卷或草案。
   *
   * @param theme 主题实体
   */
  public void assertEditableBriefing(ContentThemeEntity theme) {
    String status = theme.getStatus();
    if (ContentThemeStatus.ENABLED.getValue().equals(status)
        || ContentThemeStatus.DISABLED.getValue().equals(status)) {
      throw new BusinessException(ErrorCodes.CURRICULUM_ALREADY_COMMITTED, "已确认主题不可再改问卷或草案，请使用结构调整入口", 409);
    }
  }

  /**
   * 校验并规范化主题编码唯一性。
   *
   * @param modeId         模式 id
   * @param code           编码
   * @param excludeThemeId 排除的主题 id（更新自身时）；可空
   */
  public void ensureCodeUnique(String modeId, String code, String excludeThemeId) {
    if (!CODE_PATTERN.matcher(code).matches()) {
      throw new BusinessException(ErrorCodes.THEME_CODE_CONFLICT, "主题编码须为小写字母开头的 snake_case", 400);
    }
    if (codeTaken(modeId, code, excludeThemeId)) {
      throw new BusinessException(ErrorCodes.THEME_CODE_CONFLICT, "主题编码已存在: " + code, 409);
    }
  }

  /**
   * 规范化主题编码为小写 snake_case。
   *
   * @param raw 原始编码
   * @return 规范化编码
   */
  public String normalizeCode(String raw) {
    String s = raw.trim().toLowerCase(Locale.ROOT)
        .replace('-', '_')
        .replaceAll("[^a-z0-9_]+", "_")
        .replaceAll("_+", "_")
        .replaceAll("^_|_$", "");
    if (s.isEmpty() || !Character.isLetter(s.charAt(0))) {
      s = "theme_" + s;
    }
    if (s.length() > 64) {
      s = s.substring(0, 64);
    }
    return s;
  }

  /**
   * 组装主题视图。
   *
   * @param theme 主题实体
   * @param mode  模式实体；可空
   * @return 主题视图
   */
  public ContentThemeView toThemeView(ContentThemeEntity theme, ContentModeEntity mode) {
    return themeViews.toThemeView(theme, mode);
  }

  /**
   * 在同模式下分配未占用编码：基码可用则直接用，否则追加 _2、_3…
   *
   * @param modeId 模式 id
   * @param base   已规范化的候选编码
   * @return 可用编码
   */
  private String allocateUniqueCode(String modeId, String base) {
    if (!CODE_PATTERN.matcher(base).matches()) {
      throw new BusinessException(ErrorCodes.THEME_CODE_CONFLICT, "主题编码须为小写字母开头的 snake_case", 400);
    }
    if (!codeTaken(modeId, base, null)) {
      return base;
    }
    for (int suffix = 2; suffix <= 999; suffix++) {
      String tail = "_" + suffix;
      int maxBaseLen = Math.max(1, 64 - tail.length());
      String stem = base.length() > maxBaseLen ? base.substring(0, maxBaseLen) : base;
      // 截断后去掉尾部下划线，再拼后缀
      stem = stem.replaceAll("_+$", "");
      if (stem.isEmpty() || !Character.isLetter(stem.charAt(0))) {
        stem = "theme";
      }
      String candidate = stem + tail;
      if (!codeTaken(modeId, candidate, null)) {
        log.info("主题编码冲突，自动改用 modeId={} from={} to={}", modeId, base, candidate);
        return candidate;
      }
    }
    throw new BusinessException(ErrorCodes.THEME_CODE_CONFLICT, "主题编码已存在且无法自动分配后缀: " + base, 409);
  }

  /**
   * 同模式编码是否已被占用。
   *
   * @param modeId         模式 id
   * @param code           编码
   * @param excludeThemeId 排除的主题 id（更新自身时）；可空
   * @return 已被占用则为 true
   */
  private boolean codeTaken(String modeId, String code, String excludeThemeId) {
    ContentThemeEntity existing = themeMapper.selectOne(new LambdaQueryWrapper<ContentThemeEntity>()
        .eq(ContentThemeEntity::getModeCode, modeId)
        .eq(ContentThemeEntity::getCode, code)
        .last("LIMIT 1"));
    return existing != null
        && (excludeThemeId == null || !excludeThemeId.equals(existing.getId()));
  }

  private int nextSortOrder(String modeId) {
    ContentThemeEntity last = themeMapper.selectOne(new LambdaQueryWrapper<ContentThemeEntity>()
        .eq(ContentThemeEntity::getModeCode, modeId)
        .orderByDesc(ContentThemeEntity::getSortOrder)
        .last("LIMIT 1"));
    return last == null || last.getSortOrder() == null ? 1 : last.getSortOrder() + 1;
  }

  private String slugFromName(String name) {
    // 中文名无法直接 slug：用 hash 后缀保证唯一可读前缀
    String ascii = name.replaceAll("[^A-Za-z0-9]+", "_").toLowerCase(Locale.ROOT);
    if (ascii.length() < 2 || ascii.replace("_", "").isEmpty()) {
      return "theme_" + ContentEntityUtil.sha256(name).substring(0, 8);
    }
    return normalizeCode(ascii);
  }

  /**
   * 将课程文章指针解析为投影并写入路线视图索引。
   *
   * @param articlesById 目标索引
   * @param articleId    文章/文档 id
   */
  private void indexArticlePointer(Map<String, ArticleEntity> articlesById, String articleId) {
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
