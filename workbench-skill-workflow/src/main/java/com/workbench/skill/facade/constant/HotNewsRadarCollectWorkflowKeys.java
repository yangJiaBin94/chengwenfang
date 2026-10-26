package com.workbench.skill.facade.constant;

/**
 * 热点雷达采集内置工作流标识常量（key / 展示名 / scene）。
 */
public final class HotNewsRadarCollectWorkflowKeys {
  private HotNewsRadarCollectWorkflowKeys() {}

  /** 工作流定义唯一 key。 */
  public static final String DEFINITION_KEY = "hot-news-radar-collect";

  /** 工作流展示名。 */
  public static final String DISPLAY_NAME = "热点雷达采集";

  /** 与 JobTypes / Skill.scene 对齐的场景码。 */
  public static final String SCENE = "hot_news_radar_collect";

  /** 内置发布说明。 */
  public static final String BUILTIN_RELEASE_NOTES =
      "builtin sync radar collect: fetch enrich candidate-filter cluster ai-theme-seed rank";
}
