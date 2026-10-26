package com.chengwenfang.job.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 任务进度/生命周期事件（表 {@code job_events}），供 SSE 增量拉取。 */
@TableName("job_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobEventEntity {
  /** 自增主键。 */
  @TableId(value = "id", type = IdType.AUTO)
  private Long id;
  /** 异步任务 ID。 */
  @TableField("job_id")
  private String jobId;
  /** 事件类型：queued / started / progress / succeeded / failed / cancelled / warning 等。 */
  @TableField("event_type")
  private String eventType;
  /** 步骤标识。 */
  @TableField("step")
  private String step;
  /** 进度（0–100）。 */
  @TableField("progress")
  private Integer progress;
  /** 事件消息。 */
  @TableField("message")
  private String message;
  /** 可选 JSON 载荷（如成功输出片段）。 */
  @TableField("payload_json")
  private String payloadJson;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
}
