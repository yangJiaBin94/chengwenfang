package com.chengwenfang.sourcing.facade.constant;

/**
 * 视频解析 owner 类型（API / 服务入参；落库映射为 {@code video_transcripts} 外键）。
 */
public final class VideoParseOwnerTypes {
  /** 雷达信源 → {@code video_transcripts.source_item_id}。 */
  public static final String SOURCE_ITEM = "source_item";
  /** 选题资料池 → {@code video_transcripts.research_item_id}。 */
  public static final String TOPIC_RESEARCH_ITEM = "topic_research_item";

  private VideoParseOwnerTypes() {}
}
