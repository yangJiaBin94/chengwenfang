package com.chengwenfang.content.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 修复运行，对应 {@code repair_runs}。
 */
@TableName("repair_runs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepairRunEntity {
  /** 主键。 */
  @TableId("id")
  private String id;
  /** 产出 ID。 */
  @TableField("output_id")
  private String outputId;
  /** 异步任务 ID。 */
  @TableField("job_id")
  private String jobId;
  /** 修复前审计分。 */
  @TableField("before_score")
  private Integer beforeScore;
  /** 修复后审计分。 */
  @TableField("after_score")
  private Integer afterScore;
  /** 修复运行状态。 */
  @TableField("status")
  private String status;
  /** 失败时的错误信息。 */
  @TableField("error_message")
  private String errorMessage;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
  /** 结束时间。 */
  @TableField("finished_at")
  private LocalDateTime finishedAt;
}
