package com.workbench.common;

import cn.hutool.core.util.StrUtil;
import java.net.URI;
import java.util.Locale;

/**
 * 从来源 URL 规范化站点键（注册域风格），用于搜索词×网站拉黑。
 */
public final class SiteKeyUtil {
  private SiteKeyUtil() {}

  /**
   * 从 URL 提取站点键；失败时返回空串。
   *
   * @param url 原文链接
   * @return 小写 host，去掉 www. 前缀；无法解析时空串
   */
  public static String fromUrl(String url) {
    if (StrUtil.isBlank(url)) {
      return "";
    }
    try {
      String raw = url.trim();
      if (!raw.contains("://")) {
        raw = "https://" + raw;
      }
      URI uri = URI.create(raw);
      String host = uri.getHost();
      if (StrUtil.isBlank(host)) {
        return "";
      }
      String normalized = host.toLowerCase(Locale.ROOT);
      if (normalized.startsWith("www.")) {
        normalized = normalized.substring(4);
      }
      // 常见视频站统一到注册域
      if (normalized.endsWith(".bilibili.com") || "b23.tv".equals(normalized)) {
        return "bilibili.com";
      }
      if (normalized.endsWith(".youtube.com") || "youtu.be".equals(normalized)) {
        return "youtube.com";
      }
      return normalized;
    } catch (Exception ignored) {
      return "";
    }
  }
}
