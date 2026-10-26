package com.chengwenfang.skill.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 一次工作流运行实例，对应 workflow_runs；可与 Job 通过 jobId 关联。 */
@TableName("workflow_runs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowRunEntity {
  /** 主键 ID。 */
  @TableId("id")
  private String id;
  /** 关联后台任务 ID。 */
  @TableField("job_id")
  private String jobId;
  /** 工作流名称。 */
  @TableField("workflow_name")
  private String workflowName;
  /** 运行线程/会话 ID。 */
  @TableField("thread_id")
  private String threadId;
  /** pending / running / succeeded / failed。 */
  @TableField("status")
  private String status;
  /** 当前执行节点 key。 */
  @TableField("current_node")
  private String currentNode;
  /** 失败信息（原 state_summary.error）。 */
  @TableField("error_message")
  private String errorMessage;
  /** 工作流定义 ID。 */
  @TableField("workflow_definition_id")
  private String workflowDefinitionId;
  /** 工作流版本 ID。 */
  @TableField("workflow_version_id")
  private String workflowVersionId;
  /** 触发来源。 */
  @TableField("trigger_source")
  private String triggerSource;
  /** 启动时锁定的图定义快照。 */
  @TableField("snapshot_json")
  private String snapshotJson;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
  /** 最近更新时间。 */
  @TableField("updated_at")
  private LocalDateTime updatedAt;
  /** 结束时间。 */
  @TableField("finished_at")
  private LocalDateTime finishedAt;
}
