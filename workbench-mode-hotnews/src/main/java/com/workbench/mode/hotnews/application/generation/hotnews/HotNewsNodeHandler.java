package com.workbench.mode.hotnews.application.generation.hotnews;

import com.workbench.skill.facade.dto.view.SkillPromptBindingView;
import com.workbench.skill.facade.enums.HotNewsWorkflowNodeType;
import java.util.List;

/**
 * 热点成文单节点策略：按 {@link HotNewsWorkflowNodeType} 分发，便于扩展新节点。
 */
public interface HotNewsNodeHandler {
  /**
   * 本处理器支持的节点类型。
   *
   * @return 节点枚举
   */
  HotNewsWorkflowNodeType supports();

  /**
   * 执行节点并回写类型化状态。
   *
   * @param state          图状态（可读写）
   * @param skillBindings  节点 Skill 绑定
   * @param nodeKey        节点实例 key
   * @param nodeName       节点展示名
   */
  void execute(
      HotNewsGraphState state,
      List<SkillPromptBindingView> skillBindings,
      String nodeKey,
      String nodeName);
}
