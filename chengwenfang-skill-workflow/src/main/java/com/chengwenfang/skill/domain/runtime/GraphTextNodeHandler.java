package com.chengwenfang.skill.domain.runtime;

import java.util.List;
import java.util.Map;

/**
 * 正式路径文本节点可选钩子；缺省时 GraphRuntime 失败，不回退到 fixture。
 */
@FunctionalInterface
public interface GraphTextNodeHandler {
  /**
   * 执行文本节点：应用 skill 绑定并返回写入图状态的输出。
   */
  Map<String, Object> execute(
      String typeKey,
      String nodeKey,
      String nodeName,
      List<Map<String, Object>> skillBindings,
      Map<String, Object> state,
      Map<String, Object> config);
}
