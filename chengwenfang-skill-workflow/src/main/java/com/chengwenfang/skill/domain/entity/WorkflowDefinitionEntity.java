package com.chengwenfang.skill.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 工作流定义头表，对应 workflow_definitions；发布版本通过 publishedVersionId 指向。 */
@TableName("workflow_definitions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDefinitionEntity {
  /** 主键 ID。 */
  @TableId("id")
  private String id;
  /** 稳定业务键，如 course-content-generation。 */
  @TableField("workflow_key")
  private String workflowKey;
  /** 工作流名称。 */
  @TableField("name")
  private String name;
  /** 工作流描述。 */
  @TableField("description")
  private String description;
  /** 场景标识，用于按场景解析已发布工作流。 */
  @TableField("scene")
  private String scene;
  /** 教学模式/模式编码（列 mode_code；视图仍用 modeId）。 */
  @TableField("mode_code")
  private String modeId;
  /** active / archived。 */
  @TableField("status")
  private String status;
  /** 当前已发布版本 ID；空表示尚未发布。 */
  @TableField("published_version_id")
  private String publishedVersionId;
  /** 负责人。 */
  @TableField("owner")
  private String owner;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
  /** 最近更新时间。 */
  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
