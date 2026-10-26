package com.chengwenfang.content.application;

import cn.hutool.json.JSONUtil;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 从主题问卷提炼生成偏好默认值。
 */
@Component
@RequiredArgsConstructor
public class ThemeDefaultsBuilder {
  /** 实体与 JSON 工具。 */
  private final ContentEntityUtil support;

  /**
   * 从问卷 JSON 提炼 defaults 画像（语气、篇幅、课型等）。
   *
   * @param briefingJson 问卷 JSON；可空
   * @return defaults Map
   */
  public Map<String, Object> buildDefaultsFromBriefing(String briefingJson) {
    Map<String, Object> briefing = support.readMap(briefingJson);
    Map<String, Object> defaults = new LinkedHashMap<>();
    Object expression = briefing.get("expression");
    if (expression instanceof Map<?, ?> exp) {
      Object tone = exp.get("tone");
      Object length = exp.get("length");
      if (tone != null) {
        defaults.put("tone", tone);
      }
      if (length != null) {
        defaults.put("length", length);
        String len = String.valueOf(length);
        if ("short".equals(len)) {
          defaults.put("wordCountOption", "800-1500");
        } else if ("deep".equals(len)) {
          defaults.put("wordCountOption", "4000-6000");
        } else {
          defaults.put("wordCountOption", "2000-4000");
        }
      }
    }
    Object structure = briefing.get("structure");
    if (structure instanceof Map<?, ?> st) {
      Object forms = st.get("lesson_forms");
      if (forms != null) {
        defaults.put("lessonForms", forms);
      }
    }
    // 避免未使用告警：JSONUtil 在解析兜底时可用
    if (defaults.isEmpty() && briefingJson != null && JSONUtil.isTypeJSON(briefingJson)) {
      defaults.put("schema_version", 1);
    }
    return defaults;
  }
}
