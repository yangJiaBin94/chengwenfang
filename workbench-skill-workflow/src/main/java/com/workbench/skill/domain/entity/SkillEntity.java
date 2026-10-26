package com.workbench.skill.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Skill 提示词定义实体，对应 skills 表；运行时按节点绑定组合 system/task 指令。 */
@TableName("skills")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillEntity {
  /** 主键 ID。 */
  @TableId("id")
  private String id;
  /** Skill 名称。 */
  @TableField("name")
  private String name;
  /** Skill 描述。 */
  @TableField("description")
  private String description;
  /** 作用域：global / workflow / node。 */
  @TableField("scope")
  private String scope;
  /** Skill 类型（文本/图片等）。 */
  @TableField("skill_type")
  private String skillType;
  /** 内置锁定不可删改标记。 */
  @TableField("locked")
  private Boolean locked;
  /** 适用场景，如 course_content_generation。 */
  @TableField("scene")
  private String scene;
  /** 系统级指令（角色与硬约束）。 */
  @TableField("system_instructions")
  private String systemInstructions;
  /** 任务级指令（本节点产出要求）。 */
  @TableField("task_instructions")
  private String taskInstructions;
  /** 组合提示词时的优先级，数值越小越靠前。 */
  @TableField("priority")
  private Integer priority;
  /** 是否启用。 */
  @TableField("enabled")
  private Boolean enabled;
  /** 软删除时间。 */
  @TableField("deleted_at")
  private LocalDateTime deletedAt;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
  /** 最近更新时间。 */
  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
