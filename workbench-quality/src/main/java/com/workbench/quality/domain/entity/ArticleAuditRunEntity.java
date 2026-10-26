package com.workbench.quality.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审核运行，对应 {@code audit_runs}（课程与热点共用）。
 */
@TableName("audit_runs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleAuditRunEntity {
  /** 主键（可与任务 ID 相同）。 */
  @TableId("id")
  private String id;
  /** 产出 ID（原 document_id）。 */
  @TableField("output_id")
  private String outputId;
  /** 任务 ID。 */
  @TableField("job_id")
  private String jobId;
  /** 审核模式：deterministic / full / repair。 */
  @TableField("mode")
  private String mode;
  /** 运行状态。 */
  @TableField("status")
  private String status;
  /** 被审正文哈希。 */
  @TableField("content_hash")
  private String contentHash;
  /** 审核标准版本。 */
  @TableField("standard_version")
  private String standardVersion;
  /** 是否存在阻塞失败。 */
  @TableField("blocking_failed")
  private Boolean blockingFailed;
  /** 课程：分数。 */
  @TableField("score")
  private Integer score;
  /** 课程：是否通过。 */
  @TableField("passed")
  private Boolean passed;
  /** 课程：警告数。 */
  @TableField("warning_count")
  private Integer warningCount;
  /** 课程：失败数。 */
  @TableField("failed_count")
  private Integer failedCount;
  /** 热点：结果状态（原 result.status）。 */
  @TableField("result_status")
  private String resultStatus;
  /** 人工决定状态。 */
  @TableField("decision_status")
  private String decisionStatus;
  /** 人工决定说明。 */
  @TableField("decision_comment")
  private String decisionComment;
  /** 人工决定时间（原 LocalDateTime.now().toString()）。 */
  @TableField("decided_at")
  private String decidedAt;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
  /** 结束时间。 */
  @TableField("finished_at")
  private LocalDateTime finishedAt;
}
