package com.chengwenfang.crawler.facade;

import cn.hutool.core.util.StrUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 抓取条目发布时间解析与时效判断（供 crawler 策略与 hot-news 聚合共用）。
 */
public final class PublishTimeUtil {
  /** URL 路径中的日历日期：/2025/07/21/ 或 /2025-07-21/。 */
  private static final Pattern URL_CALENDAR_DATE = Pattern.compile(
      "/(20\\d{2})[-/](\\d{2})[-/](\\d{2})(?:/|$)");
  /** arXiv 论文号 YYMM.xxxxx → 近似发表月。 */
  private static final Pattern ARXIV_ID_DATE = Pattern.compile(
      "(?i)/(?:abs|pdf)/(\\d{2})(\\d{2})\\.\\d+");
  /** 中文年月日。 */
  private static final Pattern CHINESE_DATE = Pattern.compile(
      "(20\\d{2})\\s*年\\s*(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日?");
  /** 数字日期：2024-08-27 / 2024/08/27 / 2024.08.27。 */
  private static final Pattern NUMERIC_DATE = Pattern.compile(
      "(20\\d{2})[-/.](\\d{1,2})[-/.](\\d{1,2})");

  private PublishTimeUtil() {}

  /**
   * 解析灵活日期串（ISO、斜杠、中文年月日；供 CLI 元数据与 AI 回填共用）。
   *
   * @param raw 日期原文；可空
   * @return 本地时间；无法解析为 null
   */
  public static LocalDateTime parseFlexibleDate(String raw) {
    if (StrUtil.isBlank(raw)) {
      return null;
    }
    String value = raw.trim();
    if ((value.startsWith("\"") && value.endsWith("\""))
        || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.substring(1, value.length() - 1).trim();
    }
    try {
      if (value.length() >= 19 && value.contains("T")) {
        return LocalDateTime.parse(value.substring(0, 19));
      }
      Matcher chinese = CHINESE_DATE.matcher(value);
      if (chinese.find()) {
        int year = Integer.parseInt(chinese.group(1));
        int month = Integer.parseInt(chinese.group(2));
        int day = Integer.parseInt(chinese.group(3));
        return LocalDate.of(year, month, day).atStartOfDay();
      }
      Matcher numeric = NUMERIC_DATE.matcher(value);
      if (numeric.find()) {
        int year = Integer.parseInt(numeric.group(1));
        int month = Integer.parseInt(numeric.group(2));
        int day = Integer.parseInt(numeric.group(3));
        return LocalDate.of(year, month, day).atStartOfDay();
      }
      if (value.length() >= 10) {
        return LocalDate.parse(value.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
      }
    } catch (Exception ignored) {
      // fall through
    }
    return null;
  }

  /**
   * 优先用上游发布时间，否则从 URL 推断。
   *
   * @param published 上游字段，可空
   * @param url       链接，可空
   * @return 可用发布时间；无法确定时 null
   */
  public static LocalDateTime resolvePublishedAt(LocalDateTime published, String url) {
    if (published != null) {
      return published;
    }
    return inferPublishedAtFromUrl(url);
  }

  /**
   * 从常见资讯 URL 路径推断发布日（当日 00:00）。
   *
   * @param url 链接
   * @return 推断时间；无法识别时 null
   */
  public static LocalDateTime inferPublishedAtFromUrl(String url) {
    if (url == null || url.isBlank()) {
      return null;
    }
    Matcher calendar = URL_CALENDAR_DATE.matcher(url);
    if (calendar.find()) {
      try {
        int year = Integer.parseInt(calendar.group(1));
        int month = Integer.parseInt(calendar.group(2));
        int day = Integer.parseInt(calendar.group(3));
        return LocalDate.of(year, month, day).atStartOfDay();
      } catch (Exception ignored) {
        // fall through
      }
    }
    Matcher arxiv = ARXIV_ID_DATE.matcher(url);
    if (arxiv.find()) {
      try {
        int yy = Integer.parseInt(arxiv.group(1));
        int mm = Integer.parseInt(arxiv.group(2));
        int year = yy >= 90 ? 1900 + yy : 2000 + yy;
        return LocalDate.of(year, mm, 1).atStartOfDay();
      } catch (Exception ignored) {
        return null;
      }
    }
    return null;
  }

  /**
   * 是否应因 hours 窗口丢弃该条目。
   *
   * @param published 发布时间，可空
   * @param hours     回溯小时；null 表示不过滤
   * @return true 表示过旧应丢弃
   */
  public static boolean isStale(LocalDateTime published, Integer hours) {
    if (hours == null || published == null) {
      return false;
    }
    return published.isBefore(LocalDateTime.now().minusHours(hours));
  }

  /**
   * 搜索类渠道时效：仅丢弃「能解析且早于回溯窗口」的条目。
   * <p>无发布时间不再丢弃（由采集侧排到有时间素材之后保留）。
   *
   * @param published 已 resolve 的发布时间，可空
   * @param hours     回溯小时；null/≤0 表示不按时间窗丢弃
   * @return true 应丢弃
   */
  public static boolean isStaleOrUnknownForSearch(LocalDateTime published, Integer hours) {
    return isStale(published, hours);
  }
}
