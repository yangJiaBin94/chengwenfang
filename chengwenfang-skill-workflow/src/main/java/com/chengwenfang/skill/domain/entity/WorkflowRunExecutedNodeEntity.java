package com.chengwenfang.skill.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 工作流运行已执行节点序列，对应 workflow_run_executed_nodes（原 state_summary.executed）。 */
@TableName("workflow_run_executed_nodes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowRunExecutedNodeEntity {
  /** 主键 ID。 */
  @TableId("id")
  private String id;
  /** 所属工作流运行。 */
  @TableField("workflow_run_id")
  private String workflowRunId;
  /** 执行顺序（从 0 起）。 */
  @TableField("seq")
  private Integer seq;
  /** 已执行节点键。 */
  @TableField("node_key")
  private String nodeKey;
}
