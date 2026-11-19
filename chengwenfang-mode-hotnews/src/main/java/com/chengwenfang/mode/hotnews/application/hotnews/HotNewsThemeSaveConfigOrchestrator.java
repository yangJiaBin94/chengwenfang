package com.chengwenfang.mode.hotnews.application.hotnews;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.content.facade.ContentFacade;
import com.chengwenfang.content.facade.dto.request.ThemeBriefingPatchRequest;
import com.chengwenfang.content.facade.dto.view.ContentThemeView;
import com.chengwenfang.content.facade.dto.view.HotNewsThemeCommitView;
import com.chengwenfang.mode.hotnews.facade.HotNewsFacade;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 热点主题配置保存：双写 briefing/defaults，刷新 SearXNG 并对沉淀池渠道做 diff 同步（不重跑 AI、不删手工渠道）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotNewsThemeSaveConfigOrchestrator {
  /** 内容主题门面。 */
  private final ContentFacade content;
  /** 热点资讯门面。 */
  private final HotNewsFacade hotNews;
  /** 自动配源。 */
  private final HotNewsThemeChannelAutoWireSupport autoWire;

  /**
   * 保存热点主题配置并同步固定/池渠道。
   *
   * @param themeId 主题 id/code
   * @param request 问卷补丁
   * @return 保存结果（含当前渠道数）
   */
  @Transactional
  public HotNewsThemeCommitView saveConfig(String themeId, ThemeBriefingPatchRequest request) {
    ContentThemeView before = content.getTheme(themeId);
    if (before == null || StrUtil.isBlank(before.getId())) {
      throw new BusinessException(ErrorCodes.THEME_NOT_FOUND, "主题不存在", 404);
    }
    // 雷达/相关任务进行中禁止改配置
    if (hotNews.hasActiveJobsForTheme(before.getId())) {
      throw new BusinessException(
          ErrorCodes.THEME_INVALID_STATUS,
          "主题仍有进行中的采集或相关任务，请结束后再保存配置",
          409);
    }

    ContentThemeView saved = content.saveHotNewsConfigBriefing(themeId, request);
    Map<String, Object> briefing = readBriefing(saved.getBriefingJson());
    HotNewsThemeChannelAutoWireSupport.AutoWireResult synced =
        autoWire.syncOnSaveConfig(saved.getId(), briefing);
    log.info(
        "热点主题配置已保存并同步配源 themeId={} alive={} touched={}",
        saved.getId(),
        synced.channelCount(),
        synced.seededKeys());
    return HotNewsThemeCommitView.builder()
        .themeId(saved.getId())
        .status(saved.getStatus())
        .channelCount(synced.channelCount())
        .seededChannelTypes(synced.seededKeys())
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
      // 保存前已校验，此处回退空 map
    }
    return Map.of();
  }
}
