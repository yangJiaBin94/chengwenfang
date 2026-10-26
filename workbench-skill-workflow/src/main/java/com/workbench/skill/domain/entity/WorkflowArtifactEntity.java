package com.workbench.skill.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 节点产物（文本/JSON），对应 workflow_artifacts。 */
@TableName("workflow_artifacts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowArtifactEntity {
  /** 主键 ID。 */
  @TableId("id")
  private String id;
  /** 所属运行。 */
  @TableField("workflow_run_id")
  private String workflowRunId;
  /** 产生该产物的节点运行。 */
  @TableField("node_run_id")
  private String nodeRunId;
  /** 产物类型，如 node_output / draft_section。 */
  @TableField("artifact_type")
  private String artifactType;
  /** 内容格式：text / json。 */
  @TableField("content_format")
  private String contentFormat;
  /** 产物内容文件 ID（stored_files）。 */
  @TableField("content_file_id")
  private String contentFileId;
  /** 内容哈希。 */
  @TableField("content_hash")
  private String contentHash;
  /** 产物 schema 版本。 */
  @TableField("schema_version")
  private String schemaVersion;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
}
