package com.chengwenfang.quality.facade.dto.view;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 审核引擎输出载荷。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotArticleReviewEngineResult {
  /** 状态。 */
  private String status;
  /** 是否有阻断失败。 */
  private Boolean blockingFailed;
  /** 条目结果。 */
  private List<Map<String, Object>> results;
  /** 原始扩展字段。 */
  private Map<String, Object> extra;

  /**
   * 从松散 Map 构造（Job Handler / 引擎输出）。
   * <p>results 元素可能是 {@link Map}、{@link HotArticleRuleResultView} 或 JSON 反序列化对象。
   *
   * @param output Job/引擎输出
   * @return 引擎结果视图
   */
  public static HotArticleReviewEngineResult fromMap(Map<String, Object> output) {
    Map<String, Object> src = output == null ? Map.of() : output;
    List<Map<String, Object>> results = normalizeResultRows(src.get("results"));
    return HotArticleReviewEngineResult.builder()
        .status(src.get("status") == null ? null : String.valueOf(src.get("status")))
        .blockingFailed(Boolean.TRUE.equals(src.get("blockingFailed")))
        .results(results)
        .extra(src)
        .build();
  }

  /**
   * 将 results 列表元素统一为 Map（兼容 View / Map / JSON）。
   *
   * @param raw 原始 results 字段
   * @return Map 行列表
   */
  public static List<Map<String, Object>> normalizeResultRows(Object raw) {
    if (!(raw instanceof List<?> list) || list.isEmpty()) {
      return List.of();
    }
    List<Map<String, Object>> out = new ArrayList<>();
    for (Object row : list) {
      Map<String, Object> mapped = toResultMap(row);
      if (mapped != null) {
        out.add(mapped);
      }
    }
    return out;
  }

  /**
   * 单条规则结果转为 Map。
   *
   * @param row Map、View 或其它可序列化对象
   * @return Map；无法转换时为 null
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> toResultMap(Object row) {
    if (row == null) {
      return null;
    }
    if (row instanceof Map<?, ?> map) {
      return new LinkedHashMap<>((Map<String, Object>) map);
    }
    if (row instanceof HotArticleRuleResultView view) {
      Map<String, Object> mapped = BeanUtil.beanToMap(view, new LinkedHashMap<>(), false, true);
      if (view.getExtras() != null && !view.getExtras().isEmpty()) {
        mapped.putAll(view.getExtras());
      }
      return mapped;
    }
    try {
      return JSONUtil.parseObj(JSONUtil.toJsonStr(row));
    } catch (Exception ignored) {
      return null;
    }
  }
}
