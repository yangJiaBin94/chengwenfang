package com.chengwenfang.mode.hotnews.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 热点选题（原 topic_clusters）。 */
@TableName("hotnews_topics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicClusterEntity {
  /** 主键 ID。 */
  @TableId("id")
  private String id;
  /** 主题 ID。 */
  @TableField("theme_id")
  private String themeId;
  /** 选题标题。 */
  @TableField("title")
  private String title;
  /** 选题摘要（信源原文，可能为英文）。 */
  @TableField("summary")
  private String summary;
  /** 雷达展示用中文标题；空则回退 title。 */
  @TableField("translated_title")
  private String translatedTitle;
  /** 雷达展示用中文事实速览；空则回退 summary。 */
  @TableField("translated_summary")
  private String translatedSummary;
  /** 标题+摘要编辑哈希，内容未变则跳过重复提炼。 */
  @TableField("translation_source_hash")
  private String translationSourceHash;
  /** discovered / candidate / expired 等生命周期状态。 */
  @TableField("status")
  private String status;
  /** 热度评分。 */
  @TableField("heat_score")
  private Integer heatScore;
  /** 可写性评分，列表默认按此排序。 */
  @TableField("writing_score")
  private Integer writingScore;
  /** 证据置信分（与评分中的 evidenceScore 对应）。 */
  @TableField("confidence_score")
  private Integer confidenceScore;
  /** 速度分（近 24h 来源增量）。 */
  @TableField("velocity_score")
  private Integer velocityScore;
  /** 新鲜度分。 */
  @TableField("freshness_score")
  private Integer freshnessScore;
  /** 证据分。 */
  @TableField("evidence_score")
  private Integer evidenceScore;
  /** early / rising / peak / cooling / evergreen。 */
  @TableField("lifecycle")
  private String lifecycle;
  /** 评分理由说明。 */
  @TableField("score_reason")
  private String scoreReason;
  /** 首次发现时间。 */
  @TableField("first_seen_at")
  private LocalDateTime firstSeenAt;
  /** 最近发现时间。 */
  @TableField("last_seen_at")
  private LocalDateTime lastSeenAt;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
  /** 更新时间。 */
  @TableField("updated_at")
  private LocalDateTime updatedAt;
  /** 确认选题时间；首次确认为 confirmed 时写入。 */
  @TableField("confirmed_at")
  private LocalDateTime confirmedAt;
  /** 雷达采集批次 ID。 */
  @TableField("radar_batch_id")
  private String radarBatchId;
  /** 本批次内名次（1 起）。 */
  @TableField("radar_rank")
  private Integer radarRank;
}
