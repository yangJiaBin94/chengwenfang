package com.workbench.skill.facade.dto.request;


import com.workbench.skill.facade.dto.view.SkillNodeBindingView;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 创建 / 更新 / 幂等写入 Skill 的请求体。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillSaveRequest {
  /** 可选 ID（幂等 upsert 时使用）。 */
  private String id;
  /** 名称。 */
  @NotBlank(message = "不能为空")
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
}
