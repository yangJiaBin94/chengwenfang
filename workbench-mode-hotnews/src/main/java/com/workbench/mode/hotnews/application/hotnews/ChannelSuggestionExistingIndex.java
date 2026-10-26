package com.workbench.mode.hotnews.application.hotnews;

import cn.hutool.core.util.StrUtil;
import com.workbench.crawler.facade.enums.ChannelType;
import com.workbench.sourcing.facade.dto.view.HotNewsChannelView;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * 已有渠道去重索引。
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor
final class ChannelSuggestionExistingIndex {
  /** type|url 键集合。 */
  private final Set<String> dedupeKeys;
  /** 已登记网页 URL。 */
  private final Set<String> webUrls;
  /** 是否已有 SearXNG。 */
  private final boolean hasSearxng;
  /** 供 prompt 展示的摘要行。 */
  private final List<String> promptLines;
  /** 已启用渠道数（占配额）。 */
  private final int enabledCount;

  /**
   * 从已有渠道视图构建索引。
   *
   * @param channels 已有渠道
   * @return 索引
   */
  static ChannelSuggestionExistingIndex from(List<HotNewsChannelView> channels) {
    Set<String> keys = new LinkedHashSet<>();
    Set<String> webs = new LinkedHashSet<>();
    boolean searxng = false;
    List<String> lines = new ArrayList<>();
    if (channels == null) {
      return new ChannelSuggestionExistingIndex(keys, webs, false, lines, 0);
    }
    int enabled = 0;
    for (HotNewsChannelView channel : channels) {
      if (channel == null) {
        continue;
      }
      if (!Boolean.FALSE.equals(channel.getEnabled())) {
        enabled++;
      }
      String type = StrUtil.nullToEmpty(channel.getType());
      if (ChannelType.SEARXNG.getValue().equalsIgnoreCase(type)) {
        searxng = true;
      }
      keys.add(ChannelSuggestionValidator.dedupeKey(type, channel.getBaseUrl()));
      if (ChannelType.WEB.getValue().equalsIgnoreCase(type)) {
        webs.add(ChannelSuggestionValidator.normalizeUrl(channel.getBaseUrl()));
        if (channel.getConfig() != null) {
          for (String url : channel.getConfig().resolveUrls()) {
            if (StrUtil.isNotBlank(url)) {
              webs.add(ChannelSuggestionValidator.normalizeUrl(url));
              keys.add(ChannelSuggestionValidator.dedupeKey(ChannelType.WEB.getValue(), url));
            }
          }
        }
      }
      lines.add("- %s | %s | %s".formatted(
          StrUtil.blankToDefault(channel.getName(), "?"),
          StrUtil.blankToDefault(type, "?"),
          StrUtil.blankToDefault(channel.getBaseUrl(), "")));
    }
    return new ChannelSuggestionExistingIndex(keys, webs, searxng, lines, enabled);
  }

  /**
   * Prompt 用已有来源摘要。
   *
   * @return 多行文本
   */
  String summaryForPrompt() {
    if (promptLines.isEmpty()) {
      return "无";
    }
    return String.join("\n", promptLines);
  }

  /**
   * Prompt 用已占用网页 URL 列表（禁止再推荐）。
   *
   * @return 多行文本
   */
  String webUrlsForPrompt() {
    if (webUrls == null || webUrls.isEmpty()) {
      return "无";
    }
    List<String> lines = new ArrayList<>();
    for (String url : webUrls) {
      lines.add("- " + url);
    }
    return String.join("\n", lines);
  }
}
