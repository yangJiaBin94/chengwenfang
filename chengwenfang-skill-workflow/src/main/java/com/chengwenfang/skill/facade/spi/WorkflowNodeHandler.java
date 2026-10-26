package com.chengwenfang.skill.facade.spi;

import java.util.List;
import java.util.Map;

/**
 * 工作流图文本节点扩展点：按 typeKey 声明支持范围，经注册表按 {@link #order()} 路由。
 * <p>未命中任何实现时由正式图文本处理器走通用 LLM 回退。
 */
public interface WorkflowNodeHandler {

  /**
   * 是否处理该节点类型键。
   *
   * @param typeKey 节点 typeKey
   * @return 支持则为 true
   */
  boolean supports(String typeKey);

  /**
   * 路由优先级；数值越小越优先。同序时以 Spring 注入顺序为准。
   *
   * @return 优先级
   */
  int order();

  /**
   * 执行节点并返回写回图状态的增量。
   *
   * @param typeKey       节点类型键
   * @param nodeKey       节点实例键
   * @param nodeName      展示名
   * @param skillBindings 节点 Skill 绑定原始 Map 列表
   * @param state         图运行状态
   * @param config        节点配置
   * @return 状态增量
   */
  Map<String, Object> execute(
      String typeKey,
      String nodeKey,
      String nodeName,
      List<Map<String, Object>> skillBindings,
      Map<String, Object> state,
      Map<String, Object> config);
}
