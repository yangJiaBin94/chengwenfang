package com.chengwenfang.prompt.application.graphseed;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.prompt.facade.constant.WorkflowGraphMapKeys;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作流节点上的 提示词包绑定（种子构建用；持久化前 {@link #toMap()}）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowPromptPackBinding {

  /** 提示词包 主键 id。 */
  private String promptPackId;
  /** 提示词包名称（便于阅读与种子对照）。 */
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
    map.put(WorkflowGraphMapKeys.PROMPT_PACK_ID, promptPackId);
    map.put(WorkflowGraphMapKeys.NAME, name);
    map.put(WorkflowGraphMapKeys.PRIORITY, priority);
    map.put(WorkflowGraphMapKeys.ENABLED, enabled == null || enabled);
    return map;
  }

  /**
   * 按 提示词包 名列表解析绑定；缺 id 的名称跳过。
   *
   * @param promptPackIdsByName 提示词包 名 → id
   * @param promptPackNames     需要绑定的 提示词包 名（顺序决定优先级）
   * @return 绑定列表
   */
  public static List<WorkflowPromptPackBinding> resolve(Map<String, String> promptPackIdsByName, String... promptPackNames) {
    Map<String, String> ids = promptPackIdsByName == null ? Map.of() : promptPackIdsByName;
    List<WorkflowPromptPackBinding> bindings = new ArrayList<>();
    int priority = WorkflowGraphMapKeys.PROMPT_PACK_BINDING_PRIORITY_START;
    if (promptPackNames == null) {
      return bindings;
    }
    for (String promptPackName : promptPackNames) {
      String promptPackId = ids.get(promptPackName);
      if (StrUtil.isBlank(promptPackId)) {
        continue;
      }
      bindings.add(WorkflowPromptPackBinding.builder()
          .promptPackId(promptPackId)
          .name(promptPackName)
          .priority(priority)
          .enabled(true)
          .build());
      priority += WorkflowGraphMapKeys.PROMPT_PACK_BINDING_PRIORITY_STEP;
    }
    return bindings;
  }
}
