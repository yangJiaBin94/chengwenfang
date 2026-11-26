package com.chengwenfang.content.facade;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.content.facade.constant.HotNewsScoringRubricKeys;
import com.chengwenfang.content.facade.dto.view.ScoringRubricDimensionView;
import com.chengwenfang.content.facade.dto.view.ScoringRubricView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 主题写作价值评分量表的解析、规范化与校验。
 */
public final class ScoringRubricSupport {
  /** briefing / defaults 中的量表字段名。 */
  public static final String FIELD_SCORING_RUBRIC = "scoring_rubric";
  /** 维度数量下限。 */
  public static final int MIN_DIMENSIONS = 3;
  /** 维度数量上限。 */
  public static final int MAX_DIMENSIONS = 6;
  /** 权重合计必须等于该值。 */
  public static final int WEIGHT_SUM = 100;

  private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,47}$");

  private ScoringRubricSupport() {}

  /**
   * 从 briefing 或 defaults map 读取量表。
   *
   * @param root briefing / defaults；可空
   * @return 量表；缺失返回 null
   */
  public static ScoringRubricView readFrom(Map<String, Object> root) {
    if (root == null || root.isEmpty()) {
      return null;
    }
    Object raw = root.get(FIELD_SCORING_RUBRIC);
    if (raw == null) {
      return null;
    }
    try {
      ScoringRubricView view = JSONUtil.toBean(JSONUtil.parseObj(raw), ScoringRubricView.class);
      return normalize(view);
    } catch (Exception ignored) {
      return null;
    }
  }

  /**
   * 将量表写入 map（就地）。
   *
   * @param root   目标 map；可空则忽略
   * @param rubric 量表；可空则移除字段
   */
  public static void writeInto(Map<String, Object> root, ScoringRubricView rubric) {
    if (root == null) {
      return;
    }
    if (rubric == null) {
      root.remove(FIELD_SCORING_RUBRIC);
      return;
    }
    root.put(FIELD_SCORING_RUBRIC, toMap(normalize(rubric)));
  }

  /**
   * 规范化量表（修剪空白、校正版本默认值）。
   *
   * @param rubric 原始量表；可空
   * @return 规范化副本；输入空则 null
   */
  public static ScoringRubricView normalize(ScoringRubricView rubric) {
    if (rubric == null) {
      return null;
    }
    List<ScoringRubricDimensionView> dims = new ArrayList<>();
    if (rubric.getDimensions() != null) {
      for (ScoringRubricDimensionView dim : rubric.getDimensions()) {
        if (dim == null) {
          continue;
        }
        String key = StrUtil.trim(StrUtil.nullToEmpty(dim.getKey())).toLowerCase(Locale.ROOT);
        String label = StrUtil.trim(StrUtil.nullToEmpty(dim.getLabel()));
        String description = StrUtil.trim(StrUtil.nullToEmpty(dim.getDescription()));
        Integer weight = dim.getWeight();
        dims.add(ScoringRubricDimensionView.builder()
            .key(key)
            .label(label)
            .description(description)
            .weight(weight)
            .build());
      }
    }
    return ScoringRubricView.builder()
        .version(rubric.getVersion() == null || rubric.getVersion() < 1 ? 1 : rubric.getVersion())
        .generatedAt(StrUtil.trim(StrUtil.nullToEmpty(rubric.getGeneratedAt())))
        .dimensions(dims)
        .scoreGuidance(StrUtil.trim(StrUtil.nullToEmpty(rubric.getScoreGuidance())))
        .build();
  }

  /**
   * 校验量表是否合法；不合法抛业务异常。
   *
   * @param rubric 量表
   */
  public static void assertValid(ScoringRubricView rubric) {
    ScoringRubricView normalized = normalize(rubric);
    if (normalized == null || CollUtil.isEmpty(normalized.getDimensions())) {
      throw new BusinessException(ErrorCodes.THEME_INVALID_STATUS, "请先生成并确认评分量表", 400);
    }
    int size = normalized.getDimensions().size();
    if (size < MIN_DIMENSIONS || size > MAX_DIMENSIONS) {
      throw new BusinessException(
          ErrorCodes.THEME_INVALID_STATUS,
          "评分维度须为 " + MIN_DIMENSIONS + "～" + MAX_DIMENSIONS + " 个，当前 " + size + " 个",
          400);
    }
    Set<String> keys = new LinkedHashSet<>();
    int weightSum = 0;
    for (ScoringRubricDimensionView dim : normalized.getDimensions()) {
      if (StrUtil.isBlank(dim.getKey()) || !KEY_PATTERN.matcher(dim.getKey()).matches()) {
        throw new BusinessException(
            ErrorCodes.THEME_INVALID_STATUS,
            "维度键须为小写英文开头的 snake_case（如 audience_payoff）",
            400);
      }
      if (!keys.add(dim.getKey())) {
        throw new BusinessException(
            ErrorCodes.THEME_INVALID_STATUS, "维度键重复：" + dim.getKey(), 400);
      }
      if (StrUtil.isBlank(dim.getLabel())) {
        throw new BusinessException(ErrorCodes.THEME_INVALID_STATUS, "维度名称不能为空", 400);
      }
      if (StrUtil.isBlank(dim.getDescription())) {
        throw new BusinessException(
            ErrorCodes.THEME_INVALID_STATUS, "维度「" + dim.getLabel() + "」缺少释义", 400);
      }
      if (dim.getWeight() == null || dim.getWeight() <= 0) {
        throw new BusinessException(
            ErrorCodes.THEME_INVALID_STATUS, "维度「" + dim.getLabel() + "」权重须为正整数", 400);
      }
      weightSum += dim.getWeight();
    }
    if (weightSum != WEIGHT_SUM) {
      throw new BusinessException(
          ErrorCodes.THEME_INVALID_STATUS,
          "维度权重之和须为 " + WEIGHT_SUM + "，当前为 " + weightSum,
          400);
    }
  }

  /**
   * 是否为合法量表（不抛异常）。
   *
   * @param rubric 量表
   * @return true 合法
   */
  public static boolean isValid(ScoringRubricView rubric) {
    try {
      assertValid(rubric);
      return true;
    } catch (BusinessException ignored) {
      return false;
    }
  }

  /**
   * 按权重将各维 0～10 分合成为写作价值 0～100。
   *
   * @param rubric       量表
   * @param scoreByKey   维度 key → 0～10 分
   * @return 加权总分
   */
  public static int computeWritingScore(ScoringRubricView rubric, Map<String, Integer> scoreByKey) {
    ScoringRubricView normalized = normalize(rubric);
    if (normalized == null || CollUtil.isEmpty(normalized.getDimensions()) || scoreByKey == null) {
      return 0;
    }
    double sum = 0.0;
    for (ScoringRubricDimensionView dim : normalized.getDimensions()) {
      int raw = scoreByKey.getOrDefault(dim.getKey(), 0);
      int clamped = Math.max(0, Math.min(10, raw));
      int weight = dim.getWeight() == null ? 0 : dim.getWeight();
      sum += clamped * (weight / 100.0);
    }
    return (int) Math.round(10.0 * sum);
  }

  /**
   * 热点量表强制注入「打开欲」维：权重 {@link HotNewsScoringRubricKeys#AUDIENCE_HOOK_WEIGHT}，
   * 且不小于其它任一维；满 6 维时淘汰权重最小的非固定维。
   *
   * @param rubric 原始量表；可空
   * @return 注入后的规范化副本；输入空则 null
   */
  public static ScoringRubricView ensureAudienceHook(ScoringRubricView rubric) {
    ScoringRubricView normalized = normalize(rubric);
    if (normalized == null || CollUtil.isEmpty(normalized.getDimensions())) {
      return normalized;
    }
    List<ScoringRubricDimensionView> others = new ArrayList<>();
    for (ScoringRubricDimensionView dim : normalized.getDimensions()) {
      if (HotNewsScoringRubricKeys.AUDIENCE_HOOK.equals(dim.getKey())) {
        continue;
      }
      others.add(copyDim(dim));
    }
    // 满维时腾出位置给固定钩子维
    while (others.size() >= MAX_DIMENSIONS) {
      others.sort(Comparator.comparingInt(dim -> dim.getWeight() == null ? 0 : dim.getWeight()));
      others.remove(0);
    }
    // 其它维均不超过钩子权重时，钩子至少要让剩余权重可分配
    int hookWeight = HotNewsScoringRubricKeys.AUDIENCE_HOOK_WEIGHT;
    if (!others.isEmpty()) {
      int minFeasibleHook = (WEIGHT_SUM + others.size()) / (others.size() + 1);
      hookWeight = Math.max(hookWeight, minFeasibleHook);
    }
    int remaining = WEIGHT_SUM - hookWeight;
    redistributeWeights(others, remaining, hookWeight);
    List<ScoringRubricDimensionView> dims = new ArrayList<>();
    dims.add(ScoringRubricDimensionView.builder()
        .key(HotNewsScoringRubricKeys.AUDIENCE_HOOK)
        .label(HotNewsScoringRubricKeys.AUDIENCE_HOOK_LABEL)
        .description(HotNewsScoringRubricKeys.AUDIENCE_HOOK_DESCRIPTION)
        .weight(hookWeight)
        .build());
    dims.addAll(others);
    return ScoringRubricView.builder()
        .version(normalized.getVersion())
        .generatedAt(normalized.getGeneratedAt())
        .scoreGuidance(normalized.getScoreGuidance())
        .dimensions(dims)
        .build();
  }

  /**
   * 将维度权重按比例缩放到合计 {@code targetSum}，单维不超过 {@code maxWeight}。
   *
   * @param dims      待调整维度（就地改 weight）
   * @param targetSum 目标权重合计
   * @param maxWeight 单维上限
   */
  private static void redistributeWeights(
      List<ScoringRubricDimensionView> dims, int targetSum, int maxWeight) {
    if (dims.isEmpty() || targetSum <= 0) {
      return;
    }
    int rawSum = 0;
    for (ScoringRubricDimensionView dim : dims) {
      int weight = dim.getWeight() == null ? 1 : Math.max(1, dim.getWeight());
      dim.setWeight(weight);
      rawSum += weight;
    }
    int allocated = 0;
    for (int index = 0; index < dims.size(); index++) {
      ScoringRubricDimensionView dim = dims.get(index);
      int weight;
      if (index == dims.size() - 1) {
        weight = Math.max(1, targetSum - allocated);
      } else {
        weight = Math.max(1, (int) Math.round(dim.getWeight() * (targetSum * 1.0 / rawSum)));
        allocated += weight;
      }
      dim.setWeight(Math.min(maxWeight, weight));
    }
    // 舍入与上限裁剪后的差额回填
    int sum = dims.stream().mapToInt(ScoringRubricDimensionView::getWeight).sum();
    int leftover = targetSum - sum;
    int guard = 0;
    while (leftover != 0 && guard++ < 200) {
      boolean moved = false;
      for (ScoringRubricDimensionView dim : dims) {
        if (leftover == 0) {
          break;
        }
        int next = dim.getWeight() + (leftover > 0 ? 1 : -1);
        if (next < 1 || next > maxWeight) {
          continue;
        }
        dim.setWeight(next);
        leftover += leftover > 0 ? -1 : 1;
        moved = true;
      }
      if (!moved) {
        break;
      }
    }
  }

  private static ScoringRubricDimensionView copyDim(ScoringRubricDimensionView dim) {
    return ScoringRubricDimensionView.builder()
        .key(dim.getKey())
        .label(dim.getLabel())
        .description(dim.getDescription())
        .weight(dim.getWeight())
        .build();
  }

  /**
   * 热点量表：先注入打开欲维，再校验合法性。
   *
   * @param rubric 量表
   * @return 注入后的合法量表
   */
  public static ScoringRubricView assertValidHotNews(ScoringRubricView rubric) {
    ScoringRubricView ensured = ensureAudienceHook(rubric);
    assertValid(ensured);
    return ensured;
  }

  /**
   * 转为可写入 JSON 的 map。
   *
   * @param rubric 量表
   * @return map
   */
  public static Map<String, Object> toMap(ScoringRubricView rubric) {
    ScoringRubricView normalized = normalize(rubric);
    Map<String, Object> out = new LinkedHashMap<>();
    if (normalized == null) {
      return out;
    }
    out.put("version", normalized.getVersion());
    out.put("generatedAt", normalized.getGeneratedAt());
    out.put("scoreGuidance", normalized.getScoreGuidance());
    List<Map<String, Object>> dims = new ArrayList<>();
    for (ScoringRubricDimensionView dim : normalized.getDimensions()) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("key", dim.getKey());
      row.put("label", dim.getLabel());
      row.put("description", dim.getDescription());
      row.put("weight", dim.getWeight());
      dims.add(row);
    }
    out.put("dimensions", dims);
    return out;
  }
}
