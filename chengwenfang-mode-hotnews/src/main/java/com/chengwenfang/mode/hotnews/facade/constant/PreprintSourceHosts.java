package com.chengwenfang.mode.hotnews.facade.constant;

import cn.hutool.core.util.StrUtil;
import java.util.List;
import java.util.Locale;

/**
 * 预印本 / 论文库主机与标签关键词（与渠道配置无关，按 URL 主机推断）。
 */
public final class PreprintSourceHosts {
  /**
   * 预印本或论文库主机后缀（小写，匹配 host 等于或 endsWith {@code .} + 后缀）。
   */
  public static final List<String> HOST_SUFFIXES = List.of(
      "arxiv.org",
      "biorxiv.org",
      "medrxiv.org",
      "chemrxiv.org",
      "psyarxiv.com",
      "socarxiv.org",
      "engrxiv.org",
      "eartharxiv.org",
      "ssrn.com",
      "openreview.net",
      "aclanthology.org");

  /**
   * URL 解析失败时，对站点名 / 来源名的兜底关键词（小写包含匹配）。
   */
  public static final List<String> LABEL_KEYWORDS = List.of(
      "arxiv",
      "biorxiv",
      "medrxiv",
      "chemrxiv",
      "psyarxiv",
      "socarxiv",
      "engrxiv",
      "eartharxiv",
      "ssrn",
      "openreview",
      "acl anthology",
      "aclanthology",
      "preprint");

  private PreprintSourceHosts() {}

  /**
   * 主机是否属于预印本/论文库。
   *
   * @param host 小写主机名；可空
   * @return true 命中名单
   */
  public static boolean hostMatches(String host) {
    if (StrUtil.isBlank(host)) {
      return false;
    }
    String normalized = host.trim().toLowerCase(Locale.ROOT);
    for (String suffix : HOST_SUFFIXES) {
      if (normalized.equals(suffix) || normalized.endsWith("." + suffix)) {
        return true;
      }
    }
    return false;
  }

  /**
   * 展示名 / 来源名是否像预印本或论文库（URL 不可用时的兜底）。
   *
   * @param siteLabel  站点展示名；可空
   * @param sourceName 来源名；可空
   * @return true 命中关键词
   */
  public static boolean labelMatches(String siteLabel, String sourceName) {
    String blob = (StrUtil.nullToEmpty(siteLabel) + " " + StrUtil.nullToEmpty(sourceName))
        .toLowerCase(Locale.ROOT);
    if (StrUtil.isBlank(blob)) {
      return false;
    }
    for (String keyword : LABEL_KEYWORDS) {
      if (blob.contains(keyword)) {
        return true;
      }
    }
    return false;
  }
}
