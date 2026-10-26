package com.chengwenfang.sourcing.facade;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.crawler.facade.dto.request.ChannelFetchConfig;
import com.chengwenfang.crawler.facade.enums.ChannelType;
import com.chengwenfang.sourcing.facade.constant.HotNewsChannelLimits;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 信源渠道配额校验：主题渠道数、网页 URL 数。
 */
public final class HotNewsChannelQuotaSupport {
  private HotNewsChannelQuotaSupport() {}

  /**
   * 新建渠道前校验主题是否还有名额。
   *
   * @param existingCount 主题下已有渠道数
   */
  public static void assertCanCreate(long existingCount) {
    if (existingCount >= HotNewsChannelLimits.MAX_CHANNELS_PER_THEME) {
      throw new BusinessException(
          ErrorCodes.CHANNEL_LIMIT_EXCEEDED,
          "每个主题最多配置 "
              + HotNewsChannelLimits.MAX_CHANNELS_PER_THEME
              + " 个来源渠道",
          400);
    }
  }

  /**
   * 校验网页渠道 URL 数量不超过上限（先规范化列表内去重再计数）。
   *
   * @param type   渠道类型
   * @param config 扩展配置；可空
   * @param baseUrl 入口地址；网页且 urls 为空时计入
   */
  public static void assertWebUrlLimit(String type, ChannelFetchConfig config, String baseUrl) {
    if (!ChannelType.WEB.getValue().equalsIgnoreCase(StrUtil.nullToEmpty(type))) {
      return;
    }
    List<String> urls = resolveWebUrls(config, baseUrl);
    if (urls.size() > HotNewsChannelLimits.MAX_WEB_URLS_PER_CHANNEL) {
      throw new BusinessException(
          ErrorCodes.CHANNEL_WEB_URLS_LIMIT_EXCEEDED,
          "网页来源最多配置 "
              + HotNewsChannelLimits.MAX_WEB_URLS_PER_CHANNEL
              + " 个地址",
          400);
    }
  }

  /**
   * 解析网页 URL 列表：config.urls 优先，否则 baseUrl；列表内规范化去重保序。
   *
   * @param config  配置
   * @param baseUrl 入口
   * @return 去重后的 URL 列表
   */
  public static List<String> resolveWebUrls(ChannelFetchConfig config, String baseUrl) {
    List<String> raw = new ArrayList<>();
    if (config != null) {
      for (String url : config.resolveUrls()) {
        if (StrUtil.isNotBlank(url)) {
          raw.add(url.trim());
        }
      }
    }
    if (raw.isEmpty() && StrUtil.isNotBlank(baseUrl)) {
      raw.add(baseUrl.trim());
    }
    return dedupeUrls(raw);
  }

  /**
   * URL 列表内去重（忽略尾斜杠与大小写差异），保序。
   *
   * @param urls 原始列表
   * @return 去重结果
   */
  public static List<String> dedupeUrls(List<String> urls) {
    if (urls == null || urls.isEmpty()) {
      return List.of();
    }
    Set<String> seen = new LinkedHashSet<>();
    List<String> out = new ArrayList<>();
    for (String url : urls) {
      if (StrUtil.isBlank(url)) {
        continue;
      }
      String trimmed = url.trim();
      String key = normalizeUrlKey(trimmed);
      if (seen.contains(key)) {
        continue;
      }
      seen.add(key);
      out.add(trimmed);
    }
    return out;
  }

  /**
   * 规范化 URL 键：小写、去尾斜杠。
   *
   * @param url 原始 URL
   * @return 键
   */
  public static String normalizeUrlKey(String url) {
    String text = StrUtil.trim(StrUtil.nullToEmpty(url)).toLowerCase(Locale.ROOT);
    while (text.endsWith("/") && text.length() > 1) {
      text = text.substring(0, text.length() - 1);
    }
    return text;
  }
}
