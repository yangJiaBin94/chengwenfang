package com.workbench.skill.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 工作流不可变发布版本，对应 workflow_versions；含完整图快照。 */
@TableName("workflow_versions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowVersionEntity {
  /** 主键 ID。 */
  @TableId("id")
  private String id;
  /** 所属工作流定义。 */
  @TableField("workflow_id")
  private String workflowId;
  /** 对外版本号字符串。 */
  @TableField("version")
  private String version;
  /** 修订号。 */
  @TableField("revision")
  private Integer revision;
  /** 图定义格式版本。 */
  @TableField("schema_version")
  private Integer schemaVersion;
  /** 发布时的节点/边/绑定完整快照。 */
  @TableField("snapshot_json")
  private String snapshotJson;
  /** 发布前校验结果快照。 */
  @TableField("validation_json")
  private String validationJson;
  /** 发布说明。 */
  @TableField("release_notes")
  private String releaseNotes;
  /** 发布人。 */
  @TableField("published_by")
  private String publishedBy;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
}
