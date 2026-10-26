package com.workbench.skill.facade.dto.view;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Skill 对外视图（对齐前端 Skill 类型）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillView {
  /** Skill ID。 */
  private String id;
  /** 名称。 */
  private String name;
  /** 描述。 */
  private String description;
  /** 作用域：system / scene。 */
  private String scope;
  /** 类型：capability / instruction。 */
  private String skillType;
  /** 是否锁定。 */
  private Boolean locked;
  /** 适用场景。 */
  private String scene;
  /** 系统级指令。 */
  private String systemInstructions;
  /** 任务级指令。 */
  private String taskInstructions;
  /** 组合优先级。 */
  private Integer priority;
  /** 是否启用。 */
  private Boolean enabled;
  /** 节点绑定列表。 */
  private List<SkillNodeBindingView> nodeBindings;
  /** 应用场景列表。 */
  private List<SkillApplicationView> applications;
  /** 更新时间（ISO-8601）。 */
  private String updatedAt;
}
