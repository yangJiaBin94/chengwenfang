package com.workbench.content.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.workbench.common.IdsUtil;
import com.workbench.content.domain.entity.ThemeProfileEntity;
import com.workbench.content.domain.entity.ThemeProfileItemEntity;
import com.workbench.content.domain.entity.ThemeScoringRubricDimensionEntity;
import com.workbench.content.domain.entity.ThemeScoringRubricEntity;
import com.workbench.content.facade.constant.ThemeProfileItemTypes;
import com.workbench.content.facade.constant.ThemeProfileKinds;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 主题画像 Map 与行数据互转（键序对齐原 writeJson 下游预期）。
 */
public final class ThemeProfileAssembler {
  private ThemeProfileAssembler() {}

  /**
   * 组装 briefing Map。
   *
   * @param profile 画像行；可空
   * @param items   列表项
   * @param rubric  评分细则；可空
   * @param dims    维度列表
   * @return LinkedHashMap；profile 空则空 Map
   */
  public static Map<String, Object> toBriefingMap(
      ThemeProfileEntity profile,
      List<ThemeProfileItemEntity> items,
      ThemeScoringRubricEntity rubric,
      List<ThemeScoringRubricDimensionEntity> dims) {
    if (profile == null) {
      return new LinkedHashMap<>();
    }
    List<ThemeProfileItemEntity> safeItems = items == null ? List.of() : items;
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("schema_version", profile.getSchemaVersion() == null ? 1 : profile.getSchemaVersion());
    if (profile.getWizardStep() != null) {
      root.put("step", profile.getWizardStep());
    }
    putNaming(root, profile);
    putAudience(root, profile, safeItems);
    putGoals(root, profile, safeItems);
    putValueFilters(root, safeItems);
    putScope(root, profile, safeItems);
    putStructure(root, profile);
    // 课程：expression → radar（normalize 后置）；热点：radar → expression → scoring
    if (isCourseProfile(profile, safeItems)) {
      putExpression(root, profile);
      putRadar(root, profile, safeItems);
    } else {
      putRadar(root, profile, safeItems);
      putExpression(root, profile);
    }
    Map<String, Object> scoring = toScoringMap(rubric, dims);
    if (scoring != null) {
      root.put("scoring_rubric", scoring);
    }
    return root;
  }

  /**
   * 组装 defaults Map。
   *
   * @param profile 画像行；可空
   * @param items   列表项
   * @param rubric  评分细则；可空
   * @param dims    维度列表
   * @return LinkedHashMap；profile 空则空 Map
   */
  public static Map<String, Object> toDefaultsMap(
      ThemeProfileEntity profile,
      List<ThemeProfileItemEntity> items,
      ThemeScoringRubricEntity rubric,
      List<ThemeScoringRubricDimensionEntity> dims) {
    if (profile == null) {
      return new LinkedHashMap<>();
    }
    List<ThemeProfileItemEntity> safeItems = items == null ? List.of() : items;
    boolean hotNewsShape = isHotNewsDefaults(profile, safeItems);
    Map<String, Object> root = new LinkedHashMap<>();
    if (hotNewsShape) {
      if (profile.getDomain() != null) {
        root.put("domain", profile.getDomain());
      }
      List<String> readers = itemContents(safeItems, ThemeProfileItemTypes.READER);
      if (!readers.isEmpty()) {
        root.put("readers", readers);
      }
      if (profile.getContentStance() != null) {
        root.put("content_stance", profile.getContentStance());
      }
      List<String> worth = itemContents(safeItems, ThemeProfileItemTypes.WORTH_WRITING);
      if (!worth.isEmpty()) {
        root.put("worth_writing", worth);
      }
      List<String> mustWatch = itemContents(safeItems, ThemeProfileItemTypes.MUST_WATCH);
      if (!mustWatch.isEmpty()) {
        root.put("must_watch", mustWatch);
      }
      root.put("out_of_scope", StrUtil.nullToEmpty(profile.getOutOfScope()));
      if (profile.getLocale() != null) {
        root.put("locale", profile.getLocale());
      }
      List<String> queries = itemContents(safeItems, ThemeProfileItemTypes.SEARCH_QUERY);
      if (!queries.isEmpty()) {
        root.put("search_queries", queries);
      }
      if (profile.getGithubQuery() != null) {
        root.put("github_query", profile.getGithubQuery());
      }
      if (profile.getTone() != null) {
        root.put("tone", profile.getTone());
      }
      root.put("boundaries", StrUtil.nullToEmpty(profile.getBoundaries()));
      Map<String, Object> scoring = toScoringMap(rubric, dims);
      if (scoring != null) {
        root.put("scoring_rubric", scoring);
      }
      return root;
    }
    if (profile.getTone() != null) {
      root.put("tone", profile.getTone());
    }
    if (profile.getLengthPreference() != null) {
      root.put("length", profile.getLengthPreference());
    }
    if (profile.getWordCountOption() != null) {
      root.put("wordCountOption", profile.getWordCountOption());
    }
    List<String> forms = parseLessonForms(profile.getLessonForms());
    if (!forms.isEmpty()) {
      root.put("lessonForms", forms);
    }
    return root;
  }

  /**
   * 从 briefing/defaults Map 拆出待落库的画像行与子行。
   *
   * @param themeId     主题 id
   * @param profileKind {@link ThemeProfileKinds}
   * @param root        根 Map
   * @param updatedAt   更新时间
   * @return 拆解结果
   */
  public static AssembledProfile fromMap(
      String themeId, String profileKind, Map<String, Object> root, LocalDateTime updatedAt) {
    Map<String, Object> source = root == null ? Map.of() : root;
    boolean defaultsFlat =
        ThemeProfileKinds.DEFAULTS.equals(profileKind) && !source.containsKey("naming");
    Map<String, Object> naming = nested(source, "naming");
    Map<String, Object> audience = nested(source, "audience");
    Map<String, Object> goals = nested(source, "goals");
    Map<String, Object> scope = nested(source, "scope");
    Map<String, Object> structure = nested(source, "structure");
    Map<String, Object> expression = nested(source, "expression");
    Map<String, Object> radar = nested(source, "radar");
    Map<String, Object> valueFilters = nested(source, "value_filters");

    ThemeProfileEntity.ThemeProfileEntityBuilder builder = ThemeProfileEntity.builder()
        .themeId(themeId)
        .profileKind(profileKind)
        .schemaVersion(intOrDefault(source.get("schema_version"), 1))
        .wizardStep(intOrNull(source.get("step")))
        .updatedAt(updatedAt);

    if (defaultsFlat) {
      builder
          .domain(str(source.get("domain")))
          .contentStance(str(source.get("content_stance")))
          .outOfScope(str(source.get("out_of_scope")))
          .locale(str(source.get("locale")))
          .githubQuery(str(source.get("github_query")))
          .tone(str(source.get("tone")))
          .lengthPreference(str(source.get("length")))
          .boundaries(str(source.get("boundaries")))
          .lessonForms(toLessonFormsJson(source.get("lessonForms")))
          .wordCountOption(str(source.get("wordCountOption")));
    } else {
      Object scopeOut = scope.get("out_of_scope");
      String outOfScopeColumn = scopeOut instanceof List<?> ? null : str(scopeOut);
      builder
          .namingName(str(naming.get("name")))
          .namingDescription(str(naming.get("description")))
          .namingCode(str(naming.get("code")))
          .domain(str(naming.get("domain")))
          .domainCustom(str(naming.get("domain_custom")))
          .contentStance(str(audience.get("content_stance")))
          .readersNote(str(audience.get("readers_note")))
          .timeBudget(str(audience.get("time_budget")))
          .successCriteria(str(goals.get("success_criteria")))
          .difficultyCurve(str(scope.get("difficulty_curve")))
          .seriesCountPref(str(structure.get("series_count_pref")))
          .seriesCountCustom(intOrNull(structure.get("series_count_custom")))
          .lessonsPerSeriesPref(str(structure.get("lessons_per_series_pref")))
          .lessonsPerSeriesCustom(intOrNull(structure.get("lessons_per_series_custom")))
          .needOverviewLessons(boolOrNull(structure.get("need_overview_lessons")))
          .outOfScope(outOfScopeColumn)
          .locale(str(scope.get("locale")))
          .githubQuery(str(radar.get("github_query")))
          .tone(str(expression.get("tone")))
          .lengthPreference(str(expression.get("length")))
          .boundaries(str(expression.get("boundaries")))
          .lessonForms(toLessonFormsJson(structure.get("lesson_forms")));
    }

    List<ThemeProfileItemEntity> items = new ArrayList<>();
    if (defaultsFlat) {
      addItems(items, themeId, profileKind, ThemeProfileItemTypes.READER, source.get("readers"));
      addItems(items, themeId, profileKind, ThemeProfileItemTypes.WORTH_WRITING, source.get("worth_writing"));
      addItems(items, themeId, profileKind, ThemeProfileItemTypes.MUST_WATCH, source.get("must_watch"));
      addItems(items, themeId, profileKind, ThemeProfileItemTypes.SEARCH_QUERY, source.get("search_queries"));
      if (source.get("out_of_scope") instanceof List<?>) {
        addItems(items, themeId, profileKind, ThemeProfileItemTypes.OUT_OF_SCOPE, source.get("out_of_scope"));
      }
    } else {
      addItems(items, themeId, profileKind, ThemeProfileItemTypes.READER, audience.get("readers"));
      addItems(items, themeId, profileKind, ThemeProfileItemTypes.PREREQUISITE, audience.get("prerequisites"));
      addItems(items, themeId, profileKind, ThemeProfileItemTypes.OUTCOME, goals.get("outcomes"));
      addItems(items, themeId, profileKind, ThemeProfileItemTypes.MUST_COVER, scope.get("must_cover"));
      addItems(items, themeId, profileKind, ThemeProfileItemTypes.MUST_WATCH, scope.get("must_watch"));
      addItems(items, themeId, profileKind, ThemeProfileItemTypes.WORTH_WRITING, valueFilters.get("worth_writing"));
      addItems(items, themeId, profileKind, ThemeProfileItemTypes.SEARCH_QUERY, radar.get("search_queries"));
      addItems(items, themeId, profileKind, ThemeProfileItemTypes.SEED_CHANNEL, radar.get("seed_channels"));
      if (scope.get("out_of_scope") instanceof List<?>) {
        addItems(items, themeId, profileKind, ThemeProfileItemTypes.OUT_OF_SCOPE, scope.get("out_of_scope"));
      }
    }

    ThemeScoringRubricEntity rubricEntity = null;
    List<ThemeScoringRubricDimensionEntity> dimEntities = new ArrayList<>();
    Object rubricRaw = source.get("scoring_rubric");
    if (rubricRaw instanceof Map<?, ?> rubricMap) {
      Map<String, Object> rubric = castMap(rubricMap);
      rubricEntity = ThemeScoringRubricEntity.builder()
          .themeId(themeId)
          .profileKind(profileKind)
          .rubricVersion(intOrNull(rubric.get("version")))
          .generatedAt(str(rubric.get("generatedAt")))
          .scoreGuidance(str(rubric.get("scoreGuidance")))
          .build();
      Object dimsObj = rubric.get("dimensions");
      if (dimsObj instanceof List<?> dimList) {
        int seq = 0;
        for (Object dimObj : dimList) {
          if (!(dimObj instanceof Map<?, ?> dimMap)) {
            continue;
          }
          Map<String, Object> dim = castMap(dimMap);
          seq++;
          dimEntities.add(ThemeScoringRubricDimensionEntity.builder()
              .id(IdsUtil.newId())
              .themeId(themeId)
              .profileKind(profileKind)
              .seq(seq)
              .dimensionKey(StrUtil.blankToDefault(str(dim.get("key")), "dim_" + seq))
              .label(str(dim.get("label")))
              .description(str(dim.get("description")))
              .weight(intOrNull(dim.get("weight")))
              .build());
        }
      }
    }

    return new AssembledProfile(builder.build(), items, rubricEntity, dimEntities);
  }

  private static void putNaming(Map<String, Object> root, ThemeProfileEntity profile) {
    if (StrUtil.isAllBlank(
        profile.getNamingName(),
        profile.getNamingDescription(),
        profile.getNamingCode(),
        profile.getDomain(),
        profile.getDomainCustom())) {
      return;
    }
    Map<String, Object> naming = new LinkedHashMap<>();
    naming.put("name", StrUtil.nullToEmpty(profile.getNamingName()));
    naming.put("description", StrUtil.nullToEmpty(profile.getNamingDescription()));
    naming.put("code", StrUtil.nullToEmpty(profile.getNamingCode()));
    if (profile.getDomain() != null) {
      naming.put("domain", profile.getDomain());
    }
    // 向导样例含空串 domain_custom，读回须保留键
    if (profile.getDomainCustom() != null || isCourseProfile(profile, List.of())) {
      naming.put("domain_custom", StrUtil.nullToEmpty(profile.getDomainCustom()));
    }
    root.put("naming", naming);
  }

  private static void putAudience(
      Map<String, Object> root, ThemeProfileEntity profile, List<ThemeProfileItemEntity> items) {
    List<String> readers = itemContents(items, ThemeProfileItemTypes.READER);
    List<String> prerequisites = itemContents(items, ThemeProfileItemTypes.PREREQUISITE);
    if (readers.isEmpty()
        && prerequisites.isEmpty()
        && StrUtil.isAllBlank(profile.getReadersNote(), profile.getTimeBudget(), profile.getContentStance())) {
      return;
    }
    Map<String, Object> audience = new LinkedHashMap<>();
    if (!readers.isEmpty()) {
      audience.put("readers", readers);
    }
    if (profile.getReadersNote() != null || isCourseProfile(profile, items)) {
      audience.put("readers_note", StrUtil.nullToEmpty(profile.getReadersNote()));
    }
    if (!prerequisites.isEmpty()) {
      audience.put("prerequisites", prerequisites);
    }
    if (profile.getTimeBudget() != null) {
      audience.put("time_budget", profile.getTimeBudget());
    }
    if (profile.getContentStance() != null) {
      audience.put("content_stance", profile.getContentStance());
    }
    root.put("audience", audience);
  }

  private static void putGoals(
      Map<String, Object> root, ThemeProfileEntity profile, List<ThemeProfileItemEntity> items) {
    List<String> outcomes = itemContents(items, ThemeProfileItemTypes.OUTCOME);
    if (outcomes.isEmpty() && StrUtil.isBlank(profile.getSuccessCriteria())) {
      return;
    }
    Map<String, Object> goals = new LinkedHashMap<>();
    if (!outcomes.isEmpty()) {
      goals.put("outcomes", outcomes);
    }
    if (profile.getSuccessCriteria() != null) {
      goals.put("success_criteria", profile.getSuccessCriteria());
    }
    root.put("goals", goals);
  }

  private static void putValueFilters(Map<String, Object> root, List<ThemeProfileItemEntity> items) {
    List<String> worth = itemContents(items, ThemeProfileItemTypes.WORTH_WRITING);
    if (worth.isEmpty()) {
      return;
    }
    Map<String, Object> valueFilters = new LinkedHashMap<>();
    valueFilters.put("worth_writing", worth);
    root.put("value_filters", valueFilters);
  }

  private static void putScope(
      Map<String, Object> root, ThemeProfileEntity profile, List<ThemeProfileItemEntity> items) {
    List<String> mustCover = itemContents(items, ThemeProfileItemTypes.MUST_COVER);
    List<String> mustWatch = itemContents(items, ThemeProfileItemTypes.MUST_WATCH);
    List<String> outItems = itemContents(items, ThemeProfileItemTypes.OUT_OF_SCOPE);
    boolean hasOutItems = items.stream()
        .anyMatch(item -> ThemeProfileItemTypes.OUT_OF_SCOPE.equals(item.getItemType()));
    boolean course = isCourseProfile(profile, items);
    if (mustCover.isEmpty()
        && mustWatch.isEmpty()
        && !hasOutItems
        && StrUtil.isAllBlank(profile.getOutOfScope(), profile.getLocale(), profile.getDifficultyCurve())
        && !course) {
      return;
    }
    Map<String, Object> scope = new LinkedHashMap<>();
    if (!mustCover.isEmpty()) {
      scope.put("must_cover", mustCover);
    }
    if (!mustWatch.isEmpty()) {
      scope.put("must_watch", mustWatch);
    }
    // items 优先；课程无 items 时回空数组；热点用列
    if (hasOutItems) {
      scope.put("out_of_scope", outItems);
    } else if (course) {
      scope.put("out_of_scope", List.of());
    } else if (profile.getOutOfScope() != null) {
      scope.put("out_of_scope", profile.getOutOfScope());
    }
    if (profile.getDifficultyCurve() != null) {
      scope.put("difficulty_curve", profile.getDifficultyCurve());
    }
    if (profile.getLocale() != null) {
      scope.put("locale", profile.getLocale());
    }
    root.put("scope", scope);
  }

  private static void putStructure(Map<String, Object> root, ThemeProfileEntity profile) {
    List<String> forms = parseLessonForms(profile.getLessonForms());
    if (StrUtil.isAllBlank(
            profile.getSeriesCountPref(),
            profile.getLessonsPerSeriesPref())
        && profile.getSeriesCountCustom() == null
        && profile.getLessonsPerSeriesCustom() == null
        && profile.getNeedOverviewLessons() == null
        && forms.isEmpty()) {
      return;
    }
    Map<String, Object> structure = new LinkedHashMap<>();
    if (profile.getSeriesCountPref() != null) {
      structure.put("series_count_pref", profile.getSeriesCountPref());
    }
    structure.put("series_count_custom", profile.getSeriesCountCustom());
    if (profile.getLessonsPerSeriesPref() != null) {
      structure.put("lessons_per_series_pref", profile.getLessonsPerSeriesPref());
    }
    structure.put("lessons_per_series_custom", profile.getLessonsPerSeriesCustom());
    if (profile.getNeedOverviewLessons() != null) {
      structure.put("need_overview_lessons", profile.getNeedOverviewLessons());
    }
    if (!forms.isEmpty()) {
      structure.put("lesson_forms", forms);
    }
    root.put("structure", structure);
  }

  private static void putRadar(
      Map<String, Object> root, ThemeProfileEntity profile, List<ThemeProfileItemEntity> items) {
    List<String> queries = itemContents(items, ThemeProfileItemTypes.SEARCH_QUERY);
    List<String> channels = itemContents(items, ThemeProfileItemTypes.SEED_CHANNEL);
    boolean course = isCourseProfile(profile, items);
    // patch 时 normalizeSearchQueries 会写入空 radar；课程读回须保留该键
    if (queries.isEmpty() && channels.isEmpty() && StrUtil.isBlank(profile.getGithubQuery()) && !course) {
      return;
    }
    if (queries.isEmpty() && channels.isEmpty() && StrUtil.isBlank(profile.getGithubQuery()) && course) {
      Map<String, Object> radar = new LinkedHashMap<>();
      radar.put("search_queries", List.of());
      root.put("radar", radar);
      return;
    }
    Map<String, Object> radar = new LinkedHashMap<>();
    radar.put("search_queries", queries);
    if (profile.getGithubQuery() != null) {
      radar.put("github_query", profile.getGithubQuery());
    }
    if (!channels.isEmpty()) {
      radar.put("seed_channels", channels);
    }
    root.put("radar", radar);
  }

  private static void putExpression(Map<String, Object> root, ThemeProfileEntity profile) {
    boolean course = isCourseProfile(profile, List.of());
    if (StrUtil.isAllBlank(profile.getTone(), profile.getLengthPreference(), profile.getBoundaries())
        && !course) {
      return;
    }
    Map<String, Object> expression = new LinkedHashMap<>();
    if (profile.getTone() != null) {
      expression.put("tone", profile.getTone());
    }
    if (profile.getLengthPreference() != null) {
      expression.put("length", profile.getLengthPreference());
    }
    if (profile.getBoundaries() != null || course) {
      expression.put("boundaries", StrUtil.nullToEmpty(profile.getBoundaries()));
    }
    if (expression.isEmpty()) {
      return;
    }
    root.put("expression", expression);
  }

  /**
   * 是否课程向导画像（有结构/目标/必覆盖等课程字段）。
   *
   * @param profile 画像行
   * @param items   列表项
   * @return 课程画像时 true
   */
  private static boolean isCourseProfile(ThemeProfileEntity profile, List<ThemeProfileItemEntity> items) {
    if (profile == null) {
      return false;
    }
    if (StrUtil.isNotBlank(profile.getSeriesCountPref())
        || StrUtil.isNotBlank(profile.getLessonsPerSeriesPref())
        || profile.getNeedOverviewLessons() != null
        || StrUtil.isNotBlank(profile.getDifficultyCurve())
        || StrUtil.isNotBlank(profile.getSuccessCriteria())
        || StrUtil.isNotBlank(profile.getTimeBudget())) {
      return true;
    }
    return items.stream().anyMatch(item ->
        ThemeProfileItemTypes.MUST_COVER.equals(item.getItemType())
            || ThemeProfileItemTypes.OUTCOME.equals(item.getItemType())
            || ThemeProfileItemTypes.PREREQUISITE.equals(item.getItemType()));
  }

  private static Map<String, Object> toScoringMap(
      ThemeScoringRubricEntity rubric, List<ThemeScoringRubricDimensionEntity> dims) {
    if (rubric == null) {
      return null;
    }
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("version", rubric.getRubricVersion() == null ? 1 : rubric.getRubricVersion());
    map.put("generatedAt", StrUtil.nullToEmpty(rubric.getGeneratedAt()));
    map.put("scoreGuidance", StrUtil.nullToEmpty(rubric.getScoreGuidance()));
    List<Map<String, Object>> dimMaps = new ArrayList<>();
    if (CollUtil.isNotEmpty(dims)) {
      for (ThemeScoringRubricDimensionEntity dim : dims) {
        Map<String, Object> one = new LinkedHashMap<>();
        one.put("key", dim.getDimensionKey());
        one.put("label", StrUtil.nullToEmpty(dim.getLabel()));
        one.put("description", StrUtil.nullToEmpty(dim.getDescription()));
        one.put("weight", dim.getWeight());
        dimMaps.add(one);
      }
    }
    map.put("dimensions", dimMaps);
    return map;
  }

  private static boolean isHotNewsDefaults(ThemeProfileEntity profile, List<ThemeProfileItemEntity> items) {
    return StrUtil.isNotBlank(profile.getContentStance())
        || StrUtil.isNotBlank(profile.getLocale())
        || StrUtil.isNotBlank(profile.getGithubQuery())
        || items.stream().anyMatch(item ->
            ThemeProfileItemTypes.WORTH_WRITING.equals(item.getItemType())
                || ThemeProfileItemTypes.MUST_WATCH.equals(item.getItemType())
                || ThemeProfileItemTypes.SEARCH_QUERY.equals(item.getItemType()));
  }

  private static List<String> itemContents(List<ThemeProfileItemEntity> items, String itemType) {
    List<String> result = new ArrayList<>();
    for (ThemeProfileItemEntity item : items) {
      if (itemType.equals(item.getItemType()) && StrUtil.isNotBlank(item.getContent())) {
        result.add(item.getContent());
      }
    }
    return result;
  }

  private static void addItems(
      List<ThemeProfileItemEntity> target,
      String themeId,
      String profileKind,
      String itemType,
      Object raw) {
    if (!(raw instanceof List<?> list)) {
      return;
    }
    int seq = 0;
    for (Object item : list) {
      String content = str(item);
      if (StrUtil.isBlank(content)) {
        continue;
      }
      seq++;
      target.add(ThemeProfileItemEntity.builder()
          .id(IdsUtil.newId())
          .themeId(themeId)
          .profileKind(profileKind)
          .itemType(itemType)
          .seq(seq)
          .content(content)
          .build());
    }
  }

  private static List<String> parseLessonForms(String json) {
    if (StrUtil.isBlank(json) || !JSONUtil.isTypeJSONArray(json)) {
      return List.of();
    }
    List<String> result = new ArrayList<>();
    for (Object item : JSONUtil.parseArray(json)) {
      String text = str(item);
      if (StrUtil.isNotBlank(text)) {
        result.add(text);
      }
    }
    return result;
  }

  private static String toLessonFormsJson(Object raw) {
    if (raw == null) {
      return null;
    }
    if (raw instanceof List<?> list) {
      return JSONUtil.toJsonStr(list);
    }
    return str(raw);
  }

  private static Map<String, Object> nested(Map<String, Object> root, String key) {
    Object value = root.get(key);
    if (value instanceof Map<?, ?> map) {
      return castMap(map);
    }
    return Map.of();
  }

  private static Map<String, Object> castMap(Map<?, ?> map) {
    Map<String, Object> result = new LinkedHashMap<>();
    map.forEach((key, value) -> {
      if (key != null) {
        result.put(String.valueOf(key), value);
      }
    });
    return result;
  }

  private static String str(Object value) {
    if (value == null || value instanceof Map<?, ?> || value instanceof List<?>) {
      return null;
    }
    if (value instanceof String stringValue) {
      // 保留空串，避免向导样例键在读回时丢失
      return stringValue;
    }
    String text = StrUtil.trim(String.valueOf(value));
    return StrUtil.isBlank(text) || "null".equalsIgnoreCase(text) ? null : text;
  }

  private static Integer intOrNull(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number number) {
      return number.intValue();
    }
    String text = str(value);
    if (text == null) {
      return null;
    }
    try {
      return Integer.parseInt(text);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private static int intOrDefault(Object value, int defaultValue) {
    Integer parsed = intOrNull(value);
    return parsed == null ? defaultValue : parsed;
  }

  private static Boolean boolOrNull(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Boolean bool) {
      return bool;
    }
    if (value instanceof Number number) {
      return number.intValue() != 0;
    }
    String text = str(value);
    if (text == null) {
      return null;
    }
    return "true".equalsIgnoreCase(text) || "1".equals(text);
  }

  /**
   * 画像拆解结果。
   *
   * @param profile    画像行
   * @param items      列表项
   * @param rubric     评分细则；可空
   * @param dimensions 维度
   */
  public record AssembledProfile(
      ThemeProfileEntity profile,
      List<ThemeProfileItemEntity> items,
      ThemeScoringRubricEntity rubric,
      List<ThemeScoringRubricDimensionEntity> dimensions) {}
}
