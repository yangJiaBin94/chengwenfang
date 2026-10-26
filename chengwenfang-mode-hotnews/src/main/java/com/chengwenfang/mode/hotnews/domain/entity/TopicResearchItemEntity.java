package com.chengwenfang.mode.hotnews.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 选题调研材料条目（来源聚合或网页检索结果）。 */
@TableName("hotnews_research_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicResearchItemEntity {
  /** 主键 ID。 */
  @TableId("id")
  private String id;
  /** 研究/采集运行 ID。 */
  @TableField("run_id")
  private String runId;
  /** 选题/话题 ID。 */
  @TableField("topic_id")
  private String topicId;
  /** 材料标题。 */
  @TableField("title")
  private String title;
  /** 链接地址。 */
  @TableField("url")
  private String url;
  /** 摘要。 */
  @TableField("summary")
  private String summary;
  /** 综合评分（0～100）。 */
  @TableField("score")
  private Integer score;
  /** 材料类型：source / web 等。 */
  @TableField("kind")
  private String kind;
  /** 是否有评分明细。 */
  @TableField("has_score_details")
  private Boolean hasScoreDetails;
  /** 评分量表版本。 */
  @TableField("rubric_version")
  private Integer rubricVersion;
  /** 使用建议：priority / supplement / cautious。 */
  @TableField("recommend_use")
  private String recommendUse;
  /** 风险提示。 */
  @TableField("risk_note")
  private String riskNote;
  /**
   * 视频解析状态（内存字段；sole-write 在 {@code video_transcripts.status}）。
   */
  @TableField(exist = false)
  private String videoParseStatus;
  /**
   * 当前或失败步骤（内存字段；sole-write 在 {@code video_transcripts.step}）。
   */
  @TableField(exist = false)
  private String videoParseStep;
  /**
   * 失败原因（内存字段；sole-write 在 {@code video_transcripts.error_text}）。
   */
  @TableField(exist = false)
  private String videoParseError;
  /**
   * 字幕或 ASR 原文（内存字段；sole-write 在 {@code video_transcripts} 文本文件）。
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
  /**
   * 视频时长秒（内存字段；sole-write 在 {@code video_transcripts.duration_seconds}）。
   */
  @TableField(exist = false)
  private Integer videoDurationSeconds;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
}
