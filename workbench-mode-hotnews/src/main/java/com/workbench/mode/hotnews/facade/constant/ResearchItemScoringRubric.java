package com.workbench.mode.hotnews.facade.constant;

import com.workbench.mode.hotnews.domain.TopicDimensionScore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 资料池素材固定评分量表（设计文档五维加权；维度 0～10，综合分 0～100）。
 */
public final class ResearchItemScoringRubric {
  /** 量表版本。 */
  public static final int VERSION = 1;

  private ResearchItemScoringRubric() {}

  /**
   * 量表维度定义（权重之和为 100）。
   *
   * @return 维度快照列表（score 为空）
   */
  public static List<TopicDimensionScore> dimensions() {
    List<TopicDimensionScore> dims = new ArrayList<>();
    dims.add(dim("relevance", "相关性", 30, "是否直接服务当前选题"));
    dims.add(dim("credibility", "可信度", 30, "来源质量、内容完整性与事实表达是否可靠"));
    dims.add(dim("freshness", "时效性", 15, "是否符合当前事件时间窗口"));
    dims.add(dim("independence", "独立性", 15, "是否提供不同于已有来源的新信息"));
    dims.add(dim("writability", "可写性", 10, "是否适合转化为文章中的背景、案例或分析素材"));
    return dims;
  }

  /**
   * 按权重合成综合分：{@code round(10 × Σ(维分0~10 × 权重/100))}。
   *
   * @param scoreByKey 维度 key → 0～10 分
   * @return 0～100 综合分
   */
  public static int computeOverall(Map<String, Integer> scoreByKey) {
    Map<String, Integer> safe = scoreByKey == null ? Map.of() : scoreByKey;
    double sum = 0.0;
    for (TopicDimensionScore dim : dimensions()) {
      int raw = safe.getOrDefault(dim.getKey(), 0);
      int clamped = Math.max(0, Math.min(10, raw));
      int weight = dim.getWeight() == null ? 0 : dim.getWeight();
      sum += clamped * (weight / 100.0);
    }
    return (int) Math.round(10.0 * sum);
  }

  /**
   * 用 AI 返回的分维结果填充完整维度列表（缺维补 0）。
   *
   * @param scoreByKey  维度 key → 分数
   * @param reasonByKey 维度 key → 理由
   * @return 含 label/weight/score/reason 的列表
   */
  public static List<TopicDimensionScore> fillDimensions(
      Map<String, Integer> scoreByKey, Map<String, String> reasonByKey) {
    Map<String, Integer> scores = scoreByKey == null ? Map.of() : scoreByKey;
    Map<String, String> reasons = reasonByKey == null ? Map.of() : reasonByKey;
    List<TopicDimensionScore> out = new ArrayList<>();
    for (TopicDimensionScore dim : dimensions()) {
      int score = scores.getOrDefault(dim.getKey(), 0);
      out.add(TopicDimensionScore.builder()
          .key(dim.getKey())
          .label(dim.getLabel())
          .weight(dim.getWeight())
          .score(Math.max(0, Math.min(10, score)))
          .reason(reasons.getOrDefault(dim.getKey(), ""))
          .build());
    }
    return out;
  }

  /**
   * 规范化维度 key（小写 trim）。
   *
   * @param raw 原始 key
   * @return 规范化 key
   */
  public static String normalizeKey(String raw) {
    if (raw == null) {
      return "";
    }
    return raw.trim().toLowerCase(Locale.ROOT);
  }

  /**
   * 从分维列表构建 key→分数 Map。
   *
   * @param dims 维度列表
   * @return key → score
   */
  public static Map<String, Integer> scoreMap(List<TopicDimensionScore> dims) {
    Map<String, Integer> out = new LinkedHashMap<>();
    if (dims == null) {
      return out;
    }
    for (TopicDimensionScore dim : dims) {
      if (dim == null || dim.getKey() == null) {
        continue;
      }
      out.put(normalizeKey(dim.getKey()), dim.getScore() == null ? 0 : dim.getScore());
    }
    return out;
  }

  private static TopicDimensionScore dim(
      String key, String label, int weight, String description) {
    return TopicDimensionScore.builder()
        .key(key)
        .label(label)
        .weight(weight)
        .score(null)
        .reason(description)
        .build();
  }
}
