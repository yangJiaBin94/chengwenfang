package com.chengwenfang.skill.domain.runtime;

import com.chengwenfang.skill.facade.spi.WorkflowNodeHandler;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 工作流节点处理器注册表：按 {@link WorkflowNodeHandler#order()} 排序后，返回首个 {@code supports} 命中的实现。
 */
@Component
public class WorkflowNodeHandlerRegistry {

  /** 已按优先级排序的处理器列表。 */
  private final List<WorkflowNodeHandler> handlers;

  /**
   * @param handlers Spring 注入的全部 {@link WorkflowNodeHandler} 实现
   */
  public WorkflowNodeHandlerRegistry(List<WorkflowNodeHandler> handlers) {
    // 按 order 升序固定路由，保证与原 if 分支优先级等价
    this.handlers = handlers.stream()
        .sorted(Comparator.comparingInt(WorkflowNodeHandler::order))
        .toList();
  }

  /**
   * 查找处理指定 typeKey 的处理器；未命中表示走通用回退。
   *
   * @param typeKey 节点类型键
   * @return 命中的处理器；无则 empty
   */
  public Optional<WorkflowNodeHandler> findHandler(String typeKey) {
    for (WorkflowNodeHandler handler : handlers) {
      if (handler.supports(typeKey)) {
        return Optional.of(handler);
      }
    }
    return Optional.empty();
  }

  /**
   * @return 已排序的处理器快照（只读）
   */
  public List<WorkflowNodeHandler> handlers() {
    return handlers;
  }
}
