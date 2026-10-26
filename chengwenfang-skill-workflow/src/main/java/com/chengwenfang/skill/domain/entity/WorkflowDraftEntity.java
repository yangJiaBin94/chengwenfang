package com.chengwenfang.skill.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 工作流可编辑草稿，对应 workflow_drafts；校验通过后方可发布为版本。 */
@TableName("workflow_drafts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDraftEntity {
  /** 主键 ID。 */
  @TableId("id")
  private String id;
  /** 所属工作流定义 ID。 */
  @TableField("workflow_id")
  private String workflowId;
  /** 草稿基于的已发布版本，可为空。 */
  @TableField("base_version_id")
  private String baseVersionId;
  /** 草稿修订号。 */
  @TableField("revision")
  private Integer revision;
  /** 图定义格式版本。 */
  @TableField("schema_version")
  private Integer schemaVersion;
  /** 节点与边定义 JSON。 */
  @TableField("definition_json")
  private String definitionJson;
  /** 最近一次校验结果 JSON。 */
  @TableField("validation_json")
  private String validationJson;
  /** 最近更新人。 */
  @TableField("updated_by")
  private String updatedBy;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
  /** 最近更新时间。 */
  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
