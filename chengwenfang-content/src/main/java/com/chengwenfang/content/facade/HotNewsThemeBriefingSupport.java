package com.chengwenfang.content.facade;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.common.SearchQueryNormalizer;
import com.chengwenfang.content.facade.constant.HotNewsThemeSeedChannelKeys;
import com.chengwenfang.content.facade.dto.view.ScoringRubricView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 热点主题问卷 briefing 解析与校验。
 */
public final class HotNewsThemeBriefingSupport {
  private HotNewsThemeBriefingSupport() {}

  /**
   * 从 briefing 取出搜索关键词列表（已规范化为可检索短词）。
   *
   * @param briefing 问卷 map
   * @return 非空关键词；无则空列表
   */
  public static List<String> searchQueries(Map<String, Object> briefing) {
    Map<String, Object> radar = nestedMap(briefing, "radar");
    Object raw = radar.get("search_queries");
    List<String> queries = new ArrayList<>();
    if (raw instanceof List<?> list) {
      for (Object item : list) {
        String text = StrUtil.trim(String.valueOf(item));
        if (StrUtil.isNotBlank(text)) {
          queries.add(text);
        }
      }
    }
    return SearchQueryNormalizer.normalize(queries);
  }

  /**
   * 将 radar.search_queries 就地规范化写回 briefing，避免超长描述落库。
   *
   * @param briefing 问卷 map；可空
   */
  public static void normalizeSearchQueriesInPlace(Map<String, Object> briefing) {
    if (briefing == null) {
      return;
    }
    List<String> normalized = searchQueries(briefing);
    Object radarObj = briefing.get("radar");
    Map<String, Object> radar;
    if (radarObj instanceof Map<?, ?>) {
      radar = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : ((Map<?, ?>) radarObj).entrySet()) {
        if (entry.getKey() != null) {
          radar.put(String.valueOf(entry.getKey()), entry.getValue());
        }
      }
    } else {
      radar = new LinkedHashMap<>();
    }
    radar.put("search_queries", normalized);
    briefing.put("radar", radar);
  }

  /**
   * 从 briefing 取出 GitHub 查询串。
   *
   * @param briefing 问卷 map
   * @return 查询串；无则空串
   */
  public static String githubQuery(Map<String, Object> briefing) {
    Map<String, Object> radar = nestedMap(briefing, "radar");
    return StrUtil.trim(StrUtil.nullToEmpty(stringOrNull(radar.get("github_query"))));
  }

  /**
   * 从 briefing 取出关注实体/要点（scope.must_watch）。
   *
   * @param briefing 问卷 map
   * @return 非空条目列表；无则空列表
   */
  public static List<String> mustWatch(Map<String, Object> briefing) {
    Map<String, Object> scope = nestedMap(briefing, "scope");
    List<String> fromScope = stringList(scope.get("must_watch"));
    if (!fromScope.isEmpty()) {
      return fromScope;
    }
    // 兼容 defaults_json 扁平结构
    return stringList(briefing == null ? null : briefing.get("must_watch"));
  }

  /**
   * 从 briefing 取出主题展示名（naming.name）。
   *
   * @param briefing 问卷 map
   * @return 名称；无则空串
   */
  public static String themeName(Map<String, Object> briefing) {
    Map<String, Object> naming = nestedMap(briefing, "naming");
    return StrUtil.trim(StrUtil.nullToEmpty(stringOrNull(naming.get("name"))));
  }

  /**
   * 从 briefing 取出初始渠道类型列表（去重保序）。
   *
   * @param briefing 问卷 map
   * @return 渠道类型；缺省含 searxng
   */
  public static List<String> seedChannelTypes(Map<String, Object> briefing) {
    Map<String, Object> radar = nestedMap(briefing, "radar");
    Object raw = radar.get("seed_channels");
    Set<String> types = new LinkedHashSet<>();
    if (raw instanceof List<?> list) {
      for (Object item : list) {
        String type = StrUtil.trim(StrUtil.nullToEmpty(stringOrNull(item))).toLowerCase();
        if (HotNewsThemeSeedChannelKeys.SEARXNG.equals(type)
            || HotNewsThemeSeedChannelKeys.TAVILY.equals(type)
            || HotNewsThemeSeedChannelKeys.GITHUB.equals(type)) {
          types.add(type);
        }
      }
    }
    if (types.isEmpty()) {
      types.add(HotNewsThemeSeedChannelKeys.SEARXNG);
    }
    return new ArrayList<>(types);
  }

  /**
   * 校验热点主题启用前必填项。
   *
   * <p>必填：主题名、至少一条搜索关键词、合法评分量表（3～6 维且权重和为 100）。
   *
   * @param briefing 问卷 map
   */
  public static void assertReadyToCommit(Map<String, Object> briefing) {
    if (CollUtil.isEmpty(briefing)) {
      throw new BusinessException(ErrorCodes.THEME_INVALID_STATUS, "请先完成主题配置", 400);
    }
    Map<String, Object> naming = nestedMap(briefing, "naming");
    if (StrUtil.isBlank(stringOrNull(naming.get("name")))) {
      throw new BusinessException(ErrorCodes.THEME_INVALID_STATUS, "请填写主题名称", 400);
    }
    normalizeSearchQueriesInPlace(briefing);
    List<String> queries = searchQueries(briefing);
    if (queries.isEmpty()) {
      throw new BusinessException(
          ErrorCodes.THEME_INVALID_STATUS,
          "请至少确认一条可检索的短关键词（每条不超过 "
              + SearchQueryNormalizer.MAX_QUERY_CHARS
              + " 字，勿粘贴整段主题描述）",
          400);
    }
    List<String> seedChannels = seedChannelTypes(briefing);
    if (seedChannels.contains(HotNewsThemeSeedChannelKeys.GITHUB)
        && StrUtil.isBlank(githubQuery(briefing))) {
      throw new BusinessException(ErrorCodes.THEME_INVALID_STATUS, "已勾选 GitHub 渠道时须填写 GitHub 查询串", 400);
    }
    // 启用前必须固化合法评分量表
    ScoringRubricView rubric = ScoringRubricSupport.readFrom(briefing);
    ScoringRubricSupport.assertValid(rubric);
  }

  /**
   * 从 briefing 提炼写入 defaults_json 的摘要。
   *
   * @param briefing 问卷 map
   * @return defaults map
   */
  public static Map<String, Object> buildDefaults(Map<String, Object> briefing) {
    Map<String, Object> naming = nestedMap(briefing, "naming");
    Map<String, Object> audience = nestedMap(briefing, "audience");
    Map<String, Object> valueFilters = nestedMap(briefing, "value_filters");
    Map<String, Object> scope = nestedMap(briefing, "scope");
    Map<String, Object> expression = nestedMap(briefing, "expression");
    Map<String, Object> defaults = new LinkedHashMap<>();
    defaults.put("domain", stringOrNull(naming.get("domain")));
    defaults.put("readers", stringList(audience.get("readers")));
    defaults.put("content_stance", stringOrNull(audience.get("content_stance")));
    defaults.put("worth_writing", stringList(valueFilters.get("worth_writing")));
    defaults.put("must_watch", stringList(scope.get("must_watch")));
    defaults.put("out_of_scope", StrUtil.nullToEmpty(stringOrNull(scope.get("out_of_scope"))));
    defaults.put("locale", stringOrNull(scope.get("locale")));
    defaults.put("search_queries", searchQueries(briefing));
    defaults.put("github_query", githubQuery(briefing));
    defaults.put("tone", stringOrNull(expression.get("tone")));
    defaults.put("boundaries", StrUtil.nullToEmpty(stringOrNull(expression.get("boundaries"))));
    ScoringRubricView rubric = ScoringRubricSupport.readFrom(briefing);
    if (rubric != null) {
      ScoringRubricSupport.writeInto(defaults, rubric);
    }
    return defaults;
  }

  /**
   * 从 briefing 读取评分量表。
   *
   * @param briefing 问卷 map
   * @return 量表；无则 null
   */
  public static ScoringRubricView scoringRubric(Map<String, Object> briefing) {
    return ScoringRubricSupport.readFrom(briefing);
  }

  /**
   * 拼成文上下文用的主题说明短文。
   *
   * @param briefing 问卷 map；可空
   * @param themeName 主题名称
   * @return 说明文本；无有效信息时返回空串
   */
  public static String themeContextPrompt(Map<String, Object> briefing, String themeName) {
    if (CollUtil.isEmpty(briefing)) {
      return "";
    }
    Map<String, Object> audience = nestedMap(briefing, "audience");
    Map<String, Object> scope = nestedMap(briefing, "scope");
    Map<String, Object> expression = nestedMap(briefing, "expression");
    Map<String, Object> naming = nestedMap(briefing, "naming");
    // 兼容 defaults_json 扁平结构
    List<String> readers = stringList(audience.get("readers"));
    if (readers.isEmpty()) {
      readers = stringList(briefing.get("readers"));
    }
    String stance = StrUtil.firstNonBlank(
        stringOrNull(audience.get("content_stance")),
        stringOrNull(briefing.get("content_stance")));
    String outOfScope = StrUtil.firstNonBlank(
        stringOrNull(scope.get("out_of_scope")),
        stringOrNull(briefing.get("out_of_scope")));
    String tone = StrUtil.firstNonBlank(
        stringOrNull(expression.get("tone")),
        stringOrNull(briefing.get("tone")));
    String boundaries = StrUtil.firstNonBlank(
        stringOrNull(expression.get("boundaries")),
        stringOrNull(briefing.get("boundaries")));
    String domain = StrUtil.firstNonBlank(
        stringOrNull(naming.get("domain")),
        stringOrNull(briefing.get("domain")));
    StringBuilder builder = new StringBuilder();
    builder.append("本主题「").append(StrUtil.blankToDefault(themeName, "未命名")).append("」写作上下文：");
    if (StrUtil.isNotBlank(domain)) {
      builder.append("领域=").append(domain).append("；");
    }
    if (!readers.isEmpty()) {
      builder.append("主要读者=").append(String.join("、", readers)).append("；");
    }
    if (StrUtil.isNotBlank(stance)) {
      builder.append("内容姿态=").append(stance).append("；");
    }
    if (StrUtil.isNotBlank(tone)) {
      builder.append("语气=").append(tone).append("；");
    }
    if (StrUtil.isNotBlank(outOfScope)) {
      builder.append("明确不做=").append(outOfScope).append("；");
    }
    if (StrUtil.isNotBlank(boundaries)) {
      builder.append("敏感边界=").append(boundaries).append("；");
    }
    return builder.toString().trim();
  }

  private static Map<String, Object> nestedMap(Map<String, Object> root, String key) {
    if (root == null) {
      return Map.of();
    }
    Object value = root.get(key);
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> out = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (entry.getKey() != null) {
          out.put(String.valueOf(entry.getKey()), entry.getValue());
        }
      }
      return out;
    }
    return Map.of();
  }

  private static List<String> stringList(Object raw) {
    List<String> out = new ArrayList<>();
    if (raw instanceof List<?> list) {
      for (Object item : list) {
        String text = StrUtil.trim(StrUtil.nullToEmpty(stringOrNull(item)));
        if (StrUtil.isNotBlank(text)) {
          out.add(text);
        }
      }
    }
    return out;
  }

  private static String stringOrNull(Object value) {
    if (value == null) {
      return null;
    }
    return String.valueOf(value);
  }
}
