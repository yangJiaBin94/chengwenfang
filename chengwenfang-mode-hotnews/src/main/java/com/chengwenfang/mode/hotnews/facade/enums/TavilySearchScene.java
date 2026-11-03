package com.chengwenfang.mode.hotnews.facade.enums;

/**
 * Tavily 搜索装配场景（闭集）。
 */
public enum TavilySearchScene {
  /** 雷达固定词渠道采集。 */
  RADAR_FIXED("radar_fixed"),
  /** 雷达意图自适应搜索。 */
  RADAR_ADAPTIVE("radar_adaptive"),
  /** 选题资料池补充。 */
  TOPIC_RESEARCH("topic_research");

  private final String value;

  TavilySearchScene(String value) {
    this.value = value;
  }

  /**
   * @return 持久化/日志字面量
   */
  public String getValue() {
    return value;
  }
}
