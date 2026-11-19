package com.chengwenfang.mode.hotnews.application.hotnews;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.chengwenfang.content.facade.HotNewsThemeBriefingSupport;
import com.chengwenfang.content.facade.constant.HotNewsThemeSeedChannelKeys;
import com.chengwenfang.crawler.facade.dto.request.ChannelFetchConfig;
import com.chengwenfang.crawler.facade.enums.ChannelType;
import com.chengwenfang.mode.hotnews.facade.HotNewsFacade;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsChannelAutoWireKeys;
import com.chengwenfang.sourcing.facade.SourceChannelCatalogFacade;
import com.chengwenfang.sourcing.facade.constant.HotNewsChannelAutoWireLimits;
import com.chengwenfang.sourcing.facade.constant.HotNewsChannelLimits;
import com.chengwenfang.sourcing.facade.dto.request.HotNewsChannelCreateRequest;
import com.chengwenfang.sourcing.facade.dto.request.HotNewsChannelPatchRequest;
import com.chengwenfang.sourcing.facade.dto.view.HotNewsChannelView;
import com.chengwenfang.sourcing.facade.dto.view.SourceChannelCatalogView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 热点主题渠道自动配源：固定 SearXNG + 沉淀池标签匹配（不含 AI 补源）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotNewsThemeChannelAutoWireSupport {
  /** 热点门面。 */
  private final HotNewsFacade hotNews;
  /** 沉淀池门面。 */
  private final SourceChannelCatalogFacade catalogFacade;

  /**
   * 首次启用：软删主题下全部存活渠道后重建 SearXNG + 池匹配。
   *
   * @param themeId  主题 id
   * @param briefing 问卷
   * @return 创建结果摘要
   */
  public AutoWireResult wireOnCommit(String themeId, Map<String, Object> briefing) {
    softDeleteAllAlive(themeId);
    List<String> queries = HotNewsThemeBriefingSupport.searchQueries(briefing);
    List<String> createdKeys = new ArrayList<>();
    // 固定联网底座
    HotNewsChannelCreateRequest searxng =
        HotNewsThemeSeedChannelFactory.buildChannelRequest(
            HotNewsThemeSeedChannelKeys.SEARXNG, queries, null);
    if (searxng != null) {
      hotNews.createChannel(themeId, searxng);
      createdKeys.add(HotNewsThemeSeedChannelKeys.SEARXNG);
    }
    int remaining =
        HotNewsChannelLimits.MAX_CHANNELS_PER_THEME - createdKeys.size();
    int catalogBudget = Math.min(HotNewsChannelAutoWireLimits.MAX_CATALOG_MATCHES, remaining);
    List<SourceChannelCatalogView> matched =
        HotNewsChannelCatalogMatcher.match(briefing, catalogFacade.listEnabledCatalog(), catalogBudget);
    for (SourceChannelCatalogView entry : matched) {
      HotNewsChannelCreateRequest request = toCreateRequest(entry);
      if (request == null) {
        continue;
      }
      hotNews.createChannel(themeId, request);
      createdKeys.add(
          HotNewsChannelAutoWireKeys.CATALOG_PREFIX
              + StrUtil.blankToDefault(entry.getCode(), entry.getId()));
    }
    log.info("热点主题启用自动配源完成 themeId={} keys={}", themeId, createdKeys);
    return new AutoWireResult(createdKeys.size(), createdKeys);
  }

  /**
   * 保存配置：刷新 SearXNG queries，并对池托管渠道做 diff 同步。
   *
   * @param themeId  主题 id
   * @param briefing 问卷
   * @return 当前主题存活渠道数（近似）与本次涉及的类型键
   */
  public AutoWireResult syncOnSaveConfig(String themeId, Map<String, Object> briefing) {
    List<String> queries = HotNewsThemeBriefingSupport.searchQueries(briefing);
    List<String> touchedKeys = new ArrayList<>();
    refreshSearxng(themeId, queries, touchedKeys);
    syncCatalogOwned(themeId, briefing, touchedKeys);
    List<HotNewsChannelView> alive = hotNews.listChannels(themeId);
    int count = alive == null ? 0 : alive.size();
    log.info("热点主题配置保存配源同步完成 themeId={} alive={} touched={}", themeId, count, touchedKeys);
    return new AutoWireResult(count, touchedKeys);
  }

  /**
   * 更新或创建固定 SearXNG；已存在时保留其 enabled 状态。
   *
   * @param themeId     主题 id
   * @param queries     搜索词
   * @param touchedKeys 结果键收集
   */
  private void refreshSearxng(String themeId, List<String> queries, List<String> touchedKeys) {
    HotNewsChannelView existing = findSearxng(themeId);
    HotNewsChannelCreateRequest template =
        HotNewsThemeSeedChannelFactory.buildChannelRequest(
            HotNewsThemeSeedChannelKeys.SEARXNG, queries, null);
    if (template == null) {
      return;
    }
    if (existing == null) {
      hotNews.createChannel(themeId, template);
      touchedKeys.add(HotNewsThemeSeedChannelKeys.SEARXNG);
      return;
    }
    // 只同步名称/类型/地址/查询配置，不强制重新启用
    hotNews.patchChannel(
        existing.getId(),
        HotNewsChannelPatchRequest.builder()
            .name(template.getName())
            .type(template.getType())
            .baseUrl(template.getBaseUrl())
            .config(template.getConfig())
            .build());
    touchedKeys.add(HotNewsThemeSeedChannelKeys.SEARXNG);
  }

  /**
   * 按意图同步池托管渠道：名称+URL 同时命中沉淀池才视为托管；只增缺、只删托管且不再匹配的。
   *
   * @param themeId     主题 id
   * @param briefing    问卷
   * @param touchedKeys 结果键收集
   */
  private void syncCatalogOwned(
      String themeId, Map<String, Object> briefing, List<String> touchedKeys) {
    List<SourceChannelCatalogView> allCatalog = catalogFacade.listEnabledCatalog();
    Map<String, SourceChannelCatalogView> catalogByOwnershipKey = catalogByOwnershipKey(allCatalog);
    List<HotNewsChannelView> alive = hotNews.listChannels(themeId);
    if (alive == null) {
      alive = List.of();
    }
    Map<String, HotNewsChannelView> ownedByKey = new LinkedHashMap<>();
    for (HotNewsChannelView channel : alive) {
      if (channel == null) {
        continue;
      }
      String ownershipKey = ownershipKey(channel.getName(), channel.getBaseUrl());
      if (catalogByOwnershipKey.containsKey(ownershipKey)) {
        ownedByKey.put(ownershipKey, channel);
      }
    }
    int enabledCount = countEnabled(alive);
    int remaining = HotNewsChannelLimits.MAX_CHANNELS_PER_THEME - enabledCount;
    int catalogBudget = Math.min(
        HotNewsChannelAutoWireLimits.MAX_CATALOG_MATCHES,
        Math.max(remaining, 0) + ownedByKey.size());
    List<SourceChannelCatalogView> desired =
        HotNewsChannelCatalogMatcher.match(briefing, allCatalog, catalogBudget);
    Set<String> desiredKeys = new LinkedHashSet<>();
    for (SourceChannelCatalogView entry : desired) {
      if (entry == null || StrUtil.isBlank(entry.getBaseUrl())) {
        continue;
      }
      desiredKeys.add(ownershipKey(resolvedCatalogName(entry), entry.getBaseUrl()));
    }
    // 软删不再匹配的池托管渠道（须名称+URL 双对齐，避免误删同 URL 手工源）
    for (Map.Entry<String, HotNewsChannelView> entry : ownedByKey.entrySet()) {
      if (desiredKeys.contains(entry.getKey())) {
        continue;
      }
      HotNewsChannelView channel = entry.getValue();
      if (channel != null && StrUtil.isNotBlank(channel.getId())) {
        hotNews.deleteChannel(channel.getId());
        touchedKeys.add(HotNewsChannelAutoWireKeys.CATALOG_DROP_PREFIX + entry.getKey());
      }
    }
    // 补缺
    Set<String> existingOwnershipKeys = new HashSet<>();
    Set<String> existingUrls = new HashSet<>();
    List<HotNewsChannelView> afterDelete = hotNews.listChannels(themeId);
    if (afterDelete != null) {
      for (HotNewsChannelView channel : afterDelete) {
        if (channel == null) {
          continue;
        }
        if (StrUtil.isNotBlank(channel.getBaseUrl())) {
          existingUrls.add(normalizeUrl(channel.getBaseUrl()));
        }
        existingOwnershipKeys.add(ownershipKey(channel.getName(), channel.getBaseUrl()));
      }
    }
    int slotsLeft =
        HotNewsChannelLimits.MAX_CHANNELS_PER_THEME - countEnabled(afterDelete);
    for (SourceChannelCatalogView entry : desired) {
      if (slotsLeft <= 0) {
        break;
      }
      String key = ownershipKey(resolvedCatalogName(entry), entry.getBaseUrl());
      if (existingOwnershipKeys.contains(key)) {
        continue;
      }
      // 同 URL 已有任意渠道（含手工改名）则跳过，避免重复
      if (existingUrls.contains(normalizeUrl(entry.getBaseUrl()))) {
        continue;
      }
      HotNewsChannelCreateRequest request = toCreateRequest(entry);
      if (request == null) {
        continue;
      }
      hotNews.createChannel(themeId, request);
      existingOwnershipKeys.add(key);
      existingUrls.add(normalizeUrl(entry.getBaseUrl()));
      slotsLeft--;
      touchedKeys.add(
          HotNewsChannelAutoWireKeys.CATALOG_PREFIX
              + StrUtil.blankToDefault(entry.getCode(), entry.getId()));
    }
  }

  /**
   * 查找主题下固定 SearXNG 渠道（仅认 type，避免同名非搜索渠道被误 patch）。
   *
   * @param themeId 主题 id
   * @return 渠道；无则 null
   */
  private HotNewsChannelView findSearxng(String themeId) {
    List<HotNewsChannelView> channels = hotNews.listChannels(themeId);
    if (channels == null) {
      return null;
    }
    for (HotNewsChannelView channel : channels) {
      if (channel == null) {
        continue;
      }
      if (ChannelType.SEARXNG.getValue().equalsIgnoreCase(StrUtil.nullToEmpty(channel.getType()))) {
        return channel;
      }
    }
    return null;
  }

  /**
   * 软删主题下全部未删渠道。
   *
   * @param themeId 主题 id
   */
  private void softDeleteAllAlive(String themeId) {
    List<HotNewsChannelView> channels = hotNews.listChannels(themeId);
    if (channels == null) {
      return;
    }
    for (HotNewsChannelView channel : channels) {
      if (channel == null || StrUtil.isBlank(channel.getId())) {
        continue;
      }
      hotNews.deleteChannel(channel.getId());
    }
  }

  /**
   * 沉淀池条目转为创建请求（含 config_json）。
   *
   * @param entry 沉淀池条目
   * @return 创建请求；无效时 null
   */
  private static HotNewsChannelCreateRequest toCreateRequest(SourceChannelCatalogView entry) {
    if (entry == null || StrUtil.isBlank(entry.getBaseUrl())) {
      return null;
    }
    String type = StrUtil.blankToDefault(entry.getChannelType(), ChannelType.RSS.getValue());
    return HotNewsChannelCreateRequest.builder()
        .name(resolvedCatalogName(entry))
        .type(type)
        .baseUrl(entry.getBaseUrl().trim())
        .config(parseConfigJson(entry.getConfigJson()))
        .build();
  }

  /**
   * 沉淀池条目落库用展示名（与托管键一致）。
   *
   * @param entry 条目
   * @return 非空名称
   */
  private static String resolvedCatalogName(SourceChannelCatalogView entry) {
    return StrUtil.blankToDefault(
        StrUtil.trim(StrUtil.nullToEmpty(entry.getName())),
        StrUtil.blankToDefault(entry.getCode(), entry.getId()));
  }

  /**
   * 解析沉淀池 config_json。
   *
   * @param raw JSON；可空
   * @return 配置；空白或解析失败返回 null
   */
  private static ChannelFetchConfig parseConfigJson(String raw) {
    if (StrUtil.isBlank(raw)) {
      return null;
    }
    try {
      return JSONUtil.toBean(raw.trim(), ChannelFetchConfig.class);
    } catch (Exception ignored) {
      return null;
    }
  }

  /**
   * 按托管键索引沉淀池（名称+URL）。
   *
   * @param catalog 沉淀池
   * @return 托管键 → 条目
   */
  private static Map<String, SourceChannelCatalogView> catalogByOwnershipKey(
      List<SourceChannelCatalogView> catalog) {
    Map<String, SourceChannelCatalogView> map = new LinkedHashMap<>();
    if (catalog == null) {
      return map;
    }
    for (SourceChannelCatalogView entry : catalog) {
      if (entry == null || StrUtil.isBlank(entry.getBaseUrl())) {
        continue;
      }
      map.put(ownershipKey(resolvedCatalogName(entry), entry.getBaseUrl()), entry);
    }
    return map;
  }

  /**
   * 池托管判定键：归一化名称 + 归一化 URL，二者皆须对齐才视为池托管。
   *
   * @param name    渠道名
   * @param baseUrl 地址
   * @return 托管键
   */
  private static String ownershipKey(String name, String baseUrl) {
    return normalizeName(name) + "|" + normalizeUrl(baseUrl);
  }

  /**
   * 归一化渠道名。
   *
   * @param name 名称
   * @return 小写去空白
   */
  private static String normalizeName(String name) {
    return StrUtil.trim(StrUtil.nullToEmpty(name)).toLowerCase(Locale.ROOT);
  }

  /**
   * 归一化 URL（去尾部斜杠，与常见补源去重口径对齐）。
   *
   * @param url 地址
   * @return 小写去空白
   */
  private static String normalizeUrl(String url) {
    String normalized = StrUtil.trim(StrUtil.nullToEmpty(url)).toLowerCase(Locale.ROOT);
    while (normalized.length() > 1 && normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }

  /**
   * 统计已启用渠道数。
   *
   * @param channels 渠道列表
   * @return 启用数
   */
  private static int countEnabled(List<HotNewsChannelView> channels) {
    if (channels == null) {
      return 0;
    }
    int count = 0;
    for (HotNewsChannelView channel : channels) {
      if (channel != null && !Boolean.FALSE.equals(channel.getEnabled())) {
        count++;
      }
    }
    return count;
  }

  /**
   * 自动配源结果。
   *
   * @param channelCount 渠道数（启用时为新建数；保存时为存活数）
   * @param seededKeys   涉及的类型/池键
   */
  public record AutoWireResult(int channelCount, List<String> seededKeys) {}
}
