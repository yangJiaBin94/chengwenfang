package com.chengwenfang.content.application;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 主题问卷 FlexiblePayload 深度合并（与原 ThemeService 行为一致）。
 *
 * <p>嵌套 Map 递归合并；列表与标量（含 null）整体替换。
 */
public final class ThemeBriefingDeepMerge {
  private ThemeBriefingDeepMerge() {}

  /**
   * 将补丁深度合并进目标 Map（就地修改 target）。
   *
   * @param target 已有问卷；不可为 null
   * @param patch  补丁；可空则忽略
   */
  public static void merge(Map<String, Object> target, Map<String, Object> patch) {
    if (target == null || patch == null || patch.isEmpty()) {
      return;
    }
    for (Map.Entry<String, Object> entry : patch.entrySet()) {
      Object incoming = entry.getValue();
      Object current = target.get(entry.getKey());
      if (incoming instanceof Map<?, ?> inMap && current instanceof Map<?, ?> curMap) {
        Map<String, Object> merged = new LinkedHashMap<>();
        curMap.forEach((key, value) -> merged.put(String.valueOf(key), value));
        Map<String, Object> in = new LinkedHashMap<>();
        inMap.forEach((key, value) -> in.put(String.valueOf(key), value));
        merge(merged, in);
        target.put(entry.getKey(), merged);
      } else {
        target.put(entry.getKey(), incoming);
      }
    }
  }
}
