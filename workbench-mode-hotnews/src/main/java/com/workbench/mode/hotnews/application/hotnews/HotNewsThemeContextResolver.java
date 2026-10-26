package com.workbench.mode.hotnews.application.hotnews;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.workbench.content.facade.ContentFacade;
import com.workbench.content.facade.HotNewsThemeBriefingSupport;
import com.workbench.content.facade.dto.view.ContentThemeView;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 从内容主题问卷解析热点成文可用的主题上下文。
 */
@Component
@RequiredArgsConstructor
public class HotNewsThemeContextResolver {
  /** 内容主题门面。 */
  private final ContentFacade content;

  /**
   * 按主题 id 解析成文上下文短文。
   *
   * @param themeId 主题 id；可空
   * @return 上下文文本；无则空串
   */
  public String resolve(String themeId) {
    if (StrUtil.isBlank(themeId)) {
      return "";
    }
    try {
      ContentThemeView theme = content.getTheme(themeId);
      if (theme == null || theme.getDeletedAt() != null) {
        return "";
      }
      Map<String, Object> briefing = readJsonMap(theme.getBriefingJson());
      if (briefing.isEmpty()) {
        briefing = readJsonMap(theme.getDefaultsJson());
      }
      return HotNewsThemeBriefingSupport.themeContextPrompt(briefing, theme.getName());
    } catch (Exception ignored) {
      return "";
    }
  }

  /**
   * 解析 JSON 对象为 map。
   *
   * @param json JSON 字符串
   * @return map；失败为空
   */
  private Map<String, Object> readJsonMap(String json) {
    if (StrUtil.isBlank(json)) {
      return Map.of();
    }
    try {
      Object parsed = JSONUtil.parse(json);
      if (parsed instanceof Map<?, ?> map) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
          if (entry.getKey() != null) {
            out.put(String.valueOf(entry.getKey()), entry.getValue());
          }
        }
        return out;
      }
    } catch (Exception ignored) {
      // 忽略坏 JSON
    }
    return Map.of();
  }
}
