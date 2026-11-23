package com.chengwenfang.content.facade.constant;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 热点话题素材时效窗口（写死；抓取默认 hours 与硬闸共用）。
 *
 * <p>开启「追求时效性」后，筛选/AI 联网要求发布日落在 {@link #WINDOW_DAYS} 天内；
 * 雷达渠道抓取缺省回溯小时也使用 {@link #windowHours()}，避免与硬闸两套数字。
 * <p>给模型的提示须带 {@link #absoluteWindowInstruction()}，禁止只写「近 N 天」导致模型用错「今天」。
 */
public final class HotNewsTopicFreshness {
  /** briefing.collect_strategy 内布尔字段名。 */
  public static final String REQUIRE_TOPIC_FRESHNESS_KEY = "require_topic_freshness";

  /** 时效窗口天数（写死；抓取与硬闸共用）。 */
  public static final int WINDOW_DAYS = 7;

  private static final DateTimeFormatter ISO_DAY = DateTimeFormatter.ISO_LOCAL_DATE;

  private HotNewsTopicFreshness() {}

  /**
   * 时效窗口对应的回溯小时数（抓取默认 hours / 硬闸共用）。
   *
   * @return {@link #WINDOW_DAYS} × 24
   */
  public static int windowHours() {
    return WINDOW_DAYS * 24;
  }

  /**
   * 服务器日历「今天」。
   *
   * @return 本地日期
   */
  public static LocalDate todayDate() {
    return LocalDate.now();
  }

  /**
   * 窗口下限日期（含当日）：今天往前推 {@link #WINDOW_DAYS} 天。
   *
   * @return 本地日期
   */
  public static LocalDate windowStartDate() {
    return todayDate().minusDays(WINDOW_DAYS);
  }

  /**
   * 写入联网/brief 提示的绝对日期锚点（含今天与下限）。
   *
   * @return 多行中文说明
   */
  public static String absoluteWindowInstruction() {
    LocalDate today = todayDate();
    LocalDate start = windowStartDate();
    String todayText = today.format(ISO_DAY);
    String startText = start.format(ISO_DAY);
    return """
        今天（服务器日期）：%s
        时效窗口下限（含当日）：%s
        硬性要求：只收录原文首发日 ≥ %s 且 ≤ %s 的条目；必须以以上服务器日期为准，\
        禁止把模型训练知识里的「当前年/月」或错误年份当成今天；\
        早于窗口下限的任何日期一律视为超窗丢弃。
        """.formatted(todayText, startText, startText, todayText)
        .trim();
  }
}
