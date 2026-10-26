package com.chengwenfang.skill.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 工作流单节点一次尝试的运行记录，对应 workflow_node_runs。 */
@TableName("workflow_node_runs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowNodeRunEntity {
  /** 主键 ID。 */
  @TableId("id")
  private String id;
  /** 所属工作流运行。 */
  @TableField("workflow_run_id")
  private String workflowRunId;
  /** 节点 key。 */
  @TableField("node_key")
  private String nodeKey;
  /** 在整次运行中的执行序号。 */
  @TableField("sequence_no")
  private Integer sequenceNo;
  /** 同一节点的重试次数序号。 */
  @TableField("attempt_no")
  private Integer attemptNo;
  /** running / succeeded / failed。 */
  @TableField("status")
  private String status;
  /** 输入摘要哈希，便于幂等与对账。 */
  @TableField("input_hash")
  private String inputHash;
  /** 节点输出哈希。 */
  @TableField("output_hash")
  private String outputHash;
  /** 输入摘要。 */
  @TableField("input_summary")
  private String inputSummary;
  /** 输出摘要。 */
  @TableField("output_summary")
  private String outputSummary;
  /** 错误码。 */
  @TableField("error_code")
  private String errorCode;
  /** 错误信息。 */
  @TableField("error_message")
  private String errorMessage;
  /** 执行器版本。 */
  @TableField("executor_version")
  private String executorVersion;
  /** 执行时节点配置快照。 */
  @TableField("config_snapshot")
  private String configSnapshot;
  /** 父节点 key。 */
  @TableField("parent_node_key")
  private String parentNodeKey;
  /** 心跳时间。 */
  @TableField("heartbeat_at")
  private LocalDateTime heartbeatAt;
  /** 执行耗时（毫秒）。 */
  @TableField("duration_ms")
  private Long durationMs;
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
