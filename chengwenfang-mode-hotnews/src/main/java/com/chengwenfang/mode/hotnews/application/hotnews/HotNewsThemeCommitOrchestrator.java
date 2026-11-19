package com.chengwenfang.mode.hotnews.application.hotnews;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.chengwenfang.content.facade.ContentFacade;
import com.chengwenfang.content.facade.dto.view.ContentThemeView;
import com.chengwenfang.content.facade.dto.view.HotNewsThemeCommitView;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 热点主题确认启用：校验问卷、启用主题，并自动配置 SearXNG + 沉淀池匹配渠道。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotNewsThemeCommitOrchestrator {
  /** 内容主题门面。 */
  private final ContentFacade content;
  /** 自动配源。 */
  private final HotNewsThemeChannelAutoWireSupport autoWire;

  /**
   * 确认启用热点主题并自动配源。
   *
   * @param themeId 主题 id/code
   * @return 启用结果（含创建渠道数）
   */
  @Transactional
  public HotNewsThemeCommitView commit(String themeId) {
    HotNewsThemeCommitView enabled = content.commitHotNewsTheme(themeId);
    ContentThemeView theme = content.getTheme(enabled.getThemeId());
    Map<String, Object> briefing = readBriefing(theme.getBriefingJson());
    HotNewsThemeChannelAutoWireSupport.AutoWireResult wired =
        autoWire.wireOnCommit(theme.getId(), briefing);
    log.info(
        "热点主题已启用并自动配源 themeId={} channels={}",
        theme.getId(),
        wired.seededKeys());
    return HotNewsThemeCommitView.builder()
        .themeId(enabled.getThemeId())
        .status(enabled.getStatus())
        .channelCount(wired.channelCount())
        .seededChannelTypes(wired.seededKeys())
        .build();
  }

  /**
   * 解析 briefing JSON。
   *
   * @param json briefing 字符串
   * @return map；解析失败返回空 map
   */
  private static Map<String, Object> readBriefing(String json) {
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
      // 启用步骤已校验，此处回退空 map
    }
    return Map.of();
  }
}
