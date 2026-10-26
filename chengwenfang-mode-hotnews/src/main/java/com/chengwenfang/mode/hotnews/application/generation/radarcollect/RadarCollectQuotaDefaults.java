package com.chengwenfang.mode.hotnews.application.generation.radarcollect;

/**
 * 雷达采集获取阶段素材配额默认值。
 */
public final class RadarCollectQuotaDefaults {
  private RadarCollectQuotaDefaults() {}

  /** 单轮雷达采集最多保留的文章条数。 */
  public static final int MAX_ARTICLE_PER_FETCH = 50;

  /** 单轮雷达采集最多保留的视频条数。 */
  public static final int MAX_VIDEO_PER_FETCH = 10;
}
