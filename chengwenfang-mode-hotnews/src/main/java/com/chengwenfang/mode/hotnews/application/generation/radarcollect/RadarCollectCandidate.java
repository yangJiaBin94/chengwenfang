package com.chengwenfang.mode.hotnews.application.generation.radarcollect;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 雷达采集内存候选（原文仅驻内存，落库前丢弃）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RadarCollectCandidate {
  /** 候选临时 ID。 */
  private String tempId;
  /** 主题 ID。 */
  private String themeId;
  /** 渠道 ID。 */
  private String channelId;
  /** 渠道名。 */
  private String channelName;
  /** 渠道类型。 */
  private String channelType;
  /** URL。 */
  private String url;
  /** 标题。 */
  private String title;
  /** 作者。 */
  private String author;
  /** 发布时间。 */
  private LocalDateTime publishedAt;
  /**
   * 发布日来源键（{@link ArticlePublishDateSource#getValue()}）；文章 enrich 后写入。
   */
  private String publishDateSource;
  /** 抓取阶段摘要（可短）。 */
  private String fetchSummary;
  /** 来源站标签。 */
  private String siteLabel;
  /** 命中本条的搜索词；可空。 */
  private String searchQuery;
  /** 媒体类型 article/video。 */
  private String mediaType;
  /** 正文或口播原文（不落库）。 */
  private String rawText;
  /** 事实速览（可入库）。 */
  private String factBrief;
  /** 是否已弃用。 */
  private boolean discarded;
  /** 弃用原因。 */
  private String discardReason;
  /** 是否营销。 */
  private boolean marketing;
  /** 落库后的 source_item id。 */
  private String persistedSourceId;
}
