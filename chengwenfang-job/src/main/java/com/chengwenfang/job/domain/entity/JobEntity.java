package com.chengwenfang.job.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chengwenfang.job.facade.constant.JobTypes;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 异步任务实体（表 {@code jobs}）。 */
@TableName("jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobEntity {
  /** 主键 ID。 */
  @TableId("id")
  private String id;
  /** 任务类型，见 {@link JobTypes}。 */
  @TableField("job_type")
  private String jobType;
  /** 状态：pending / running / succeeded / failed / cancelled。 */
  @TableField("status")
  private String status;
  /** 关联业务实体类型（如 article、course）。 */
  @TableField("entity_type")
  private String entityType;
  /** 业务实体 ID。 */
  @TableField("entity_id")
  private String entityId;
  /** 所属主题（统计与按主题查活动任务用）。 */
  @TableField("theme_id")
  private String themeId;
  /** 所属作品（统计与排查用；写入方在后续任务补齐）。 */
  @TableField("work_id")
  private String workId;
  /** 进度（0–100）。 */
  @TableField("progress")
  private Integer progress;
  /** 当前步骤标识。 */
  @TableField("current_step")
  private String currentStep;
  /** 任务输入 JSON。 */
  @TableField("input_json")
  private String inputJson;
  /** 任务输出 JSON。 */
  @TableField("output_json")
  private String outputJson;
  /** 错误码。 */
  @TableField(value = "error_code", updateStrategy = FieldStrategy.ALWAYS)
  private String errorCode;
  /** 错误信息。 */
  @TableField(value = "error_message", updateStrategy = FieldStrategy.ALWAYS)
  private String errorMessage;
  /** 已重试次数。 */
  @TableField("retry_count")
  private Integer retryCount;
  /** 最大重试次数。 */
  @TableField("max_retries")
  private Integer maxRetries;
  /** 租约过期时间；过期后 running 任务可被重新认领。 */
  @TableField(value = "lease_expires_at", updateStrategy = FieldStrategy.ALWAYS)
  private LocalDateTime leaseExpiresAt;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
  /** 开始执行时间。 */
  @TableField(value = "started_at", updateStrategy = FieldStrategy.ALWAYS)
  private LocalDateTime startedAt;
  /** 结束时间。 */
  @TableField(value = "finished_at", updateStrategy = FieldStrategy.ALWAYS)
  private LocalDateTime finishedAt;
}
