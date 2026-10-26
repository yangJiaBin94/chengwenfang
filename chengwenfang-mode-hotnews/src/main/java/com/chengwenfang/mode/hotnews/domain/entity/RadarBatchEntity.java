package com.chengwenfang.mode.hotnews.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 雷达采集批次。 */
@TableName("hotnews_radar_batches")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RadarBatchEntity {
  /** 批次主键。 */
  @TableId("id")
  private String id;
  /** 内容主题 ID。 */
  @TableField("theme_id")
  private String themeId;
  /** 触发 Job ID。 */
  @TableField("job_id")
  private String jobId;
  /** 状态：running / completed / failed。 */
  @TableField("status")
  private String status;
  /** 开始时间。 */
  @TableField("started_at")
  private LocalDateTime startedAt;
  /** 结束时间。 */
  @TableField("finished_at")
  private LocalDateTime finishedAt;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
}
