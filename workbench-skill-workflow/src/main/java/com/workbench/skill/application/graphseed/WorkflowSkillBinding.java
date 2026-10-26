package com.workbench.skill.application.graphseed;

import cn.hutool.core.util.StrUtil;
import com.workbench.skill.facade.constant.WorkflowGraphMapKeys;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作流节点上的 Skill 绑定（种子构建用；持久化前 {@link #toMap()}）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowSkillBinding {

  /** Skill 主键 id。 */
  private String skillId;
  /** Skill 名称（便于阅读与种子对照）。 */
  private String name;
  /** 组合提示词时的优先级（越小越靠前）。 */
  private Integer priority;
  /** 是否启用该绑定。 */
  private Boolean enabled;

  /**
   * 转为可写入定义 JSON 的 Map。
   *
   * @return 有序 Map
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put(WorkflowGraphMapKeys.SKILL_ID, skillId);
    map.put(WorkflowGraphMapKeys.NAME, name);
    map.put(WorkflowGraphMapKeys.PRIORITY, priority);
    map.put(WorkflowGraphMapKeys.ENABLED, enabled == null || enabled);
    return map;
  }

  /**
   * 按 Skill 名列表解析绑定；缺 id 的名称跳过。
   *
   * @param skillIdsByName Skill 名 → id
   * @param skillNames     需要绑定的 Skill 名（顺序决定优先级）
   * @return 绑定列表
   */
  public static List<WorkflowSkillBinding> resolve(Map<String, String> skillIdsByName, String... skillNames) {
    Map<String, String> ids = skillIdsByName == null ? Map.of() : skillIdsByName;
    List<WorkflowSkillBinding> bindings = new ArrayList<>();
    int priority = WorkflowGraphMapKeys.SKILL_BINDING_PRIORITY_START;
    if (skillNames == null) {
      return bindings;
    }
    for (String skillName : skillNames) {
      String skillId = ids.get(skillName);
      if (StrUtil.isBlank(skillId)) {
        continue;
      }
      bindings.add(WorkflowSkillBinding.builder()
          .skillId(skillId)
          .name(skillName)
          .priority(priority)
          .enabled(true)
          .build());
      priority += WorkflowGraphMapKeys.SKILL_BINDING_PRIORITY_STEP;
    }
    return bindings;
  }
}
