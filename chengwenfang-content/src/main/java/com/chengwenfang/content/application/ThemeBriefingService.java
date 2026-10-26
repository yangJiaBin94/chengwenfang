package com.chengwenfang.content.application;

import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.content.domain.entity.ContentModeEntity;
import com.chengwenfang.content.domain.entity.ContentThemeEntity;
import com.chengwenfang.content.facade.HotNewsThemeBriefingSupport;
import com.chengwenfang.content.facade.constant.ContentModeIds;
import com.chengwenfang.content.facade.dto.request.ThemeBriefingPatchRequest;
import com.chengwenfang.content.facade.dto.view.ContentThemeView;
import com.chengwenfang.content.facade.dto.view.HotNewsThemeCommitView;
import com.chengwenfang.content.facade.enums.ContentThemeStatus;
import com.chengwenfang.content.infrastructure.mapper.ContentThemeMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 主题问卷读写与合并，以及热点主题确认启用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThemeBriefingService {
  /** 主题表。 */
  private final ContentThemeMapper themeMapper;
  /** 主题画像结构化存储。 */
  private final ThemeProfileStore profileStore;
  /** 主题生命周期（解析与视图）。 */
  private final ThemeLifecycleService lifecycle;

  /**
   * 合并保存问卷。
   *
   * @param themeId 主题 id/code
   * @param request 补丁
   * @return 更新后主题
   */
  @Transactional
  public ContentThemeView patchBriefing(String themeId, ThemeBriefingPatchRequest request) {
    ContentThemeEntity theme = lifecycle.requireActiveTheme(themeId);
    lifecycle.assertEditableBriefing(theme);
    ThemeBriefingPatchRequest body = request == null ? new ThemeBriefingPatchRequest() : request;
    Map<String, Object> existing = new LinkedHashMap<>(profileStore.loadBriefing(theme.getId()));
    if (body.getBriefing() != null) {
      ThemeBriefingDeepMerge.merge(existing, body.getBriefing().mutableMap());
    }
    existing.putIfAbsent("schema_version", 1);

    if (body.getName() != null && !body.getName().isBlank()) {
      theme.setName(body.getName().trim());
      putNested(existing, "naming", "name", theme.getName());
    }
    if (body.getDescription() != null) {
      theme.setDescription(body.getDescription().trim());
      putNested(existing, "naming", "description", theme.getDescription());
    }
    if (body.getCode() != null && !body.getCode().isBlank()) {
      if (!ContentThemeStatus.DRAFT.getValue().equals(theme.getStatus())
          && !ContentThemeStatus.READY_FOR_REVIEW.getValue().equals(theme.getStatus())) {
        throw new BusinessException(ErrorCodes.THEME_INVALID_STATUS, "已确认主题不可修改编码", 400);
      }
      String code = lifecycle.normalizeCode(body.getCode());
      lifecycle.ensureCodeUnique(theme.getModeCode(), code, theme.getId());
      theme.setCode(code);
      putNested(existing, "naming", "code", code);
    }
    // 从 naming 回填列
    Object namingObj = existing.get("naming");
    if (namingObj instanceof Map<?, ?> namingMap) {
      Object n = namingMap.get("name");
      if (n != null && !String.valueOf(n).isBlank()) {
        theme.setName(String.valueOf(n).trim());
      }
      Object d = namingMap.get("description");
      if (d != null) {
        theme.setDescription(String.valueOf(d).trim());
      }
      Object c = namingMap.get("code");
      if (c != null && !String.valueOf(c).isBlank()
          && (ContentThemeStatus.DRAFT.getValue().equals(theme.getStatus())
              || ContentThemeStatus.READY_FOR_REVIEW.getValue().equals(theme.getStatus()))) {
        String code = lifecycle.normalizeCode(String.valueOf(c));
        lifecycle.ensureCodeUnique(theme.getModeCode(), code, theme.getId());
        theme.setCode(code);
      }
    }

    // 搜索词在保存时规范化，避免把主题描述整段写进 radar.search_queries
    HotNewsThemeBriefingSupport.normalizeSearchQueriesInPlace(existing);
    LocalDateTime now = LocalDateTime.now();
    profileStore.saveBriefing(theme.getId(), existing, now);
    theme.setUpdatedAt(now);
    themeMapper.updateById(theme);
    return lifecycle.getTheme(theme.getId());
  }

  /**
   * 确认热点主题问卷并启用（不创建渠道；渠道由应用层编排）。
   *
   * @param themeId 主题 id/code
   * @return 启用结果骨架（渠道数由编排层回填）
   */
  @Transactional
  public HotNewsThemeCommitView commitHotNewsTheme(String themeId) {
    ContentThemeEntity theme = lifecycle.requireActiveTheme(themeId);
    ContentModeEntity mode = lifecycle.requireMode(theme.getModeCode());
    if (!ContentModeIds.isHotNews(mode.getCode(), mode.getCode())) {
      throw new BusinessException(ErrorCodes.THEME_INVALID_STATUS, "仅热点资讯主题支持该确认操作", 400);
    }
    if (!ContentThemeStatus.DRAFT.getValue().equals(theme.getStatus())) {
      throw new BusinessException(ErrorCodes.THEME_INVALID_STATUS, "仅草稿主题可确认启用", 400);
    }
    Map<String, Object> briefing = new LinkedHashMap<>(profileStore.loadBriefing(theme.getId()));
    HotNewsThemeBriefingSupport.assertReadyToCommit(briefing);
    List<String> seedChannels = HotNewsThemeBriefingSupport.seedChannelTypes(briefing);
    LocalDateTime now = LocalDateTime.now();
    profileStore.saveBriefing(theme.getId(), briefing, now);
    profileStore.saveDefaults(theme.getId(), HotNewsThemeBriefingSupport.buildDefaults(briefing), now);
    theme.setStatus(ContentThemeStatus.ENABLED.getValue());
    theme.setUpdatedAt(now);
    themeMapper.updateById(theme);
    log.info("热点主题已启用 themeId={} seedChannels={}", theme.getId(), seedChannels);
    return HotNewsThemeCommitView.builder()
        .themeId(theme.getId())
        .status(theme.getStatus())
        .channelCount(0)
        .seededChannelTypes(seedChannels)
        .build();
  }

  private static void putNested(Map<String, Object> root, String parentKey, String childKey, Object value) {
    Object parent = root.get(parentKey);
    Map<String, Object> map;
    if (parent instanceof Map<?, ?> existing) {
      map = new LinkedHashMap<>();
      existing.forEach((k, v) -> map.put(String.valueOf(k), v));
    } else {
      map = new LinkedHashMap<>();
    }
    map.put(childKey, value);
    root.put(parentKey, map);
  }
}
