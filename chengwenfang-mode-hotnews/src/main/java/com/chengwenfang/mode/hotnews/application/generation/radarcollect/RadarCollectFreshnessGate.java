package com.chengwenfang.mode.hotnews.application.generation.radarcollect;

import com.chengwenfang.content.facade.constant.HotNewsTopicFreshness;
import com.chengwenfang.crawler.facade.PublishTimeUtil;
import java.time.LocalDateTime;

/**
 * 雷达采集话题素材时效硬闸。
 *
 * <p>开启时：无发布日或早于近 {@link HotNewsTopicFreshness#WINDOW_DAYS} 天均应丢弃。
 */
public final class RadarCollectFreshnessGate {
  private RadarCollectFreshnessGate() {}

  /**
   * 是否因时效硬闸应丢弃。
   *
   * @param publishedAt           发布日；可空
   * @param requireTopicFreshness 主题是否开启时效
   * @return true 应丢弃
   */
  public static boolean shouldDiscard(LocalDateTime publishedAt, boolean requireTopicFreshness) {
    if (!requireTopicFreshness) {
      return false;
    }
    if (publishedAt == null) {
      return true;
    }
    return PublishTimeUtil.isStale(publishedAt, HotNewsTopicFreshness.windowHours());
  }

  /**
   * 丢弃原因文案（仅当 {@link #shouldDiscard} 为 true 时有意义）。
   *
   * @param publishedAt 发布日；可空
   * @return 中文原因
   */
  public static String discardReason(LocalDateTime publishedAt) {
    if (publishedAt == null) {
      return "时效硬闸：无发布日期";
    }
    return "时效硬闸：发布日早于近 " + HotNewsTopicFreshness.WINDOW_DAYS + " 天";
  }
}
