package com.workbench.skill.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Skill 与工作流节点类型的默认绑定，对应 skill_default_bindings。 */
@TableName("skill_default_bindings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillNodeBindingEntity {
  /** 主键 ID。 */
  @TableId("id")
  private String id;
  /** 绑定的 Skill。 */
  @TableField("skill_id")
  private String skillId;
  /** 工作流类型/场景。 */
  @TableField("workflow_type")
  private String workflowType;
  /** 节点 typeKey 或 key。 */
  @TableField("node_key")
  private String nodeKey;
  /** 组合优先级。 */
  @TableField("priority")
  private Integer priority;
  /** 是否启用。 */
  @TableField("enabled")
  private Boolean enabled;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
  /** 最近更新时间。 */
  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
