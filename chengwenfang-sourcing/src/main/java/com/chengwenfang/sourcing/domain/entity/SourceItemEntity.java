package com.chengwenfang.sourcing.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 信源抓取到的单条资讯条目。 */
@TableName("source_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceItemEntity {
  /** 主键 ID。 */
  @TableId("id")
  private String id;
  /** 主题 ID。 */
  @TableField("theme_id")
  private String themeId;
  /** 信源渠道 ID。 */
  @TableField("channel_id")
  private String channelId;
  /** 频道内去重键（通常为 URL 或标题）。 */
  @TableField("external_id")
  private String externalId;
  /** 链接地址。 */
  @TableField("url")
  private String url;
  /** 条目标题。 */
  @TableField("title")
  private String title;
  /** 作者。 */
  @TableField("author")
  private String author;
  /** 发布时间。 */
  @TableField("published_at")
  private LocalDateTime publishedAt;
  /** 摘要（视频解析成功后为事实速览）。 */
  @TableField("summary")
  private String summary;
  /** 媒体类型 article/video。 */
  @TableField("media_type")
  private String mediaType;
  /** 来源站展示名。 */
  @TableField("site_label")
  private String siteLabel;
  /** 生命周期 ready/pending_parse/discarded。 */
  @TableField("lifecycle_status")
  private String lifecycleStatus;
  /** 弃用原因。 */
  @TableField("discard_reason")
  private String discardReason;
  /** 内容意图 knowledge/marketing。 */
  @TableField("content_intent")
  private String contentIntent;
  /**
   * 视频解析状态（内存字段；sole-write 在 {@code video_transcripts.status}）。
   */
  @TableField(exist = false)
  private String videoParseStatus;
  /**
   * 视频解析步骤（内存字段；sole-write 在 {@code video_transcripts.step}）。
   */
  @TableField(exist = false)
  private String videoParseStep;
  /**
   * 视频解析错误（内存字段；sole-write 在 {@code video_transcripts.error_text}）。
   */
  @TableField(exist = false)
  private String videoParseError;
  /**
   * 字幕/ASR 原文（内存字段；sole-write 在 {@code video_transcripts} 文本文件）。
   */
  @TableField(exist = false)
  private String videoRawText;
  /**
   * 解析成功时间（内存字段；sole-write 在 {@code video_transcripts.parsed_at}）。
   */
  @TableField(exist = false)
  private LocalDateTime videoParsedAt;
  /**
   * 视频内容类型 speech/visual（内存字段；sole-write 在 {@code video_transcripts.content_kind}）。
   */
  @TableField(exist = false)
  private String videoContentKind;
  /** 内容哈希。 */
  @TableField("content_hash")
  private String contentHash;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
}
