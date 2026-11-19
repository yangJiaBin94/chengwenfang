package com.chengwenfang.mode.hotnews.application.hotnews;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.sourcing.facade.constant.HotNewsChannelAutoWireLimits;
import com.chengwenfang.sourcing.facade.dto.view.SourceChannelCatalogView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 按主题场景标签与沉淀池条目标签求交，选出待克隆渠道。
 */
public final class HotNewsChannelCatalogMatcher {
  private HotNewsChannelCatalogMatcher() {}

  /**
   * 匹配沉淀池条目。
   *
   * @param briefing      主题问卷
   * @param catalog       启用中的沉淀池
   * @param maxMatches    最多返回条数；&lt;=0 表示不匹配（返回空列表）
   * @return 已排序的命中条目
   */
  public static List<SourceChannelCatalogView> match(
      Map<String, Object> briefing,
      List<SourceChannelCatalogView> catalog,
      int maxMatches) {
    if (maxMatches <= 0) {
      return List.of();
    }
    int limit = Math.min(maxMatches, HotNewsChannelAutoWireLimits.MAX_CATALOG_MATCHES);
    List<String> themeTags = HotNewsThemeCatalogTagInferrer.inferTags(briefing);
    if (themeTags.isEmpty() || CollUtil.isEmpty(catalog)) {
      return List.of();
    }
    Set<String> themeTagSet = new HashSet<>();
    for (String tag : themeTags) {
      if (StrUtil.isNotBlank(tag)) {
        themeTagSet.add(tag.trim().toLowerCase(Locale.ROOT));
      }
    }
    List<SourceChannelCatalogView> matched = new ArrayList<>();
    for (SourceChannelCatalogView entry : catalog) {
      if (entry == null || StrUtil.isBlank(entry.getBaseUrl())) {
        continue;
      }
      if (!intersects(themeTagSet, entry.getTags())) {
        continue;
      }
      matched.add(entry);
      if (matched.size() >= limit) {
        break;
      }
    }
    return matched;
  }

  /**
   * 主题标签与条目标签是否有交集。
   *
   * @param themeTags 主题标签集合
   * @param entryTags 条目标签
   * @return 有交集则为 true
   */
  private static boolean intersects(Set<String> themeTags, List<String> entryTags) {
    if (CollUtil.isEmpty(entryTags)) {
      return false;
    }
    for (String tag : entryTags) {
      if (StrUtil.isBlank(tag)) {
        continue;
      }
      if (themeTags.contains(tag.trim().toLowerCase(Locale.ROOT))) {
        return true;
      }
    }
    return false;
  }
}
