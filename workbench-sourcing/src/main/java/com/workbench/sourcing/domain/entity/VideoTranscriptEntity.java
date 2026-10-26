package com.workbench.sourcing.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 视频解析结果行，对应 {@code video_transcripts}。
 */
@TableName("video_transcripts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoTranscriptEntity {
  /** 主键。 */
  @TableId("id")
  private String id;
  /** 信源条目 id（与 researchItemId 二选一）。 */
  @TableField("source_item_id")
  private String sourceItemId;
  /** 资料池条目 id（与 sourceItemId 二选一）。 */
  @TableField("research_item_id")
  private String researchItemId;
  /** 视频 URL 快照。 */
  @TableField("url")
  private String url;
  /** 解析状态。 */
  @TableField("status")
  private String status;
  /** 当前或失败步骤。 */
  @TableField("step")
  private String step;
  /** 失败原因。 */
  @TableField("error_text")
  private String errorText;
  /** 字幕或 ASR 原文文件 id。 */
  @TableField("text_file_id")
  private String textFileId;
  /** speech / visual。 */
  @TableField("content_kind")
  private String contentKind;
  /** 时长秒。 */
  @TableField("duration_seconds")
  private Integer durationSeconds;
  /** 解析成功时间。 */
  @TableField("parsed_at")
  private LocalDateTime parsedAt;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
  /** 更新时间。 */
  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
