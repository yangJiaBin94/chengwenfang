package com.workbench.mode.hotnews.application.generation;

import com.workbench.mode.hotnews.application.generation.hotnews.HotNewsGraphState;
import com.workbench.mode.hotnews.application.generation.hotnews.HotNewsGraphStateKeys;
import com.workbench.mode.hotnews.application.generation.hotnews.HotNewsNodeHandler;
import com.workbench.skill.facade.constant.WorkflowNodeHandlerOrders;
import com.workbench.skill.facade.dto.view.SkillPromptBindingView;
import com.workbench.skill.facade.enums.HotNewsWorkflowNodeType;
import com.workbench.skill.facade.spi.WorkflowNodeHandler;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 热点成文工作流节点调度器：按 {@link HotNewsWorkflowNodeType} 分发到策略处理器。
 */
@Slf4j
@Component
public class HotNewsArticleGraphExecutor implements WorkflowNodeHandler {
  /** 节点类型 → 处理器。 */
  private final Map<HotNewsWorkflowNodeType, HotNewsNodeHandler> handlersByType;

  /**
   * 收集全部 {@link HotNewsNodeHandler} 并建立枚举索引。
   *
   * @param handlers Spring 注入的全部热点节点处理器
   */
  public HotNewsArticleGraphExecutor(List<HotNewsNodeHandler> handlers) {
    Map<HotNewsWorkflowNodeType, HotNewsNodeHandler> indexed = new EnumMap<>(HotNewsWorkflowNodeType.class);
    for (HotNewsNodeHandler handler : handlers) {
      indexed.put(handler.supports(), handler);
    }
    this.handlersByType = Map.copyOf(indexed);
  }

  /**
   * @param typeKey 节点类型键
   * @return 是否为热点成文节点（hot_news_ 前缀）
   */
  @Override
  public boolean supports(String typeKey) {
    return HotNewsWorkflowNodeType.isHotNewsTypeKey(typeKey);
  }

  /**
   * @return 热点成文节点路由优先级
   */
  @Override
  public int order() {
    return WorkflowNodeHandlerOrders.HOT_NEWS;
  }

  /**
   * 执行单个热点节点，返回写回 GraphRuntime 的状态增量。
   *
   * @param typeKey        节点类型键
   * @param nodeKey        节点实例键
   * @param nodeName       展示名
   * @param skillBindings  原始 Skill 绑定 Map 列表（工作流快照格式）
   * @param state          图运行状态
   * @param config         节点配置（当前策略未强依赖）
   * @return 状态增量
   */
  @Override
  public Map<String, Object> execute(
      String typeKey,
      String nodeKey,
      String nodeName,
      List<Map<String, Object>> skillBindings,
      Map<String, Object> state,
      Map<String, Object> config) {
    HotNewsWorkflowNodeType nodeType = HotNewsWorkflowNodeType.from(typeKey)
        .orElseThrow(() -> new IllegalArgumentException("未知热点节点类型：" + typeKey));
    HotNewsNodeHandler handler = handlersByType.get(nodeType);
    if (handler == null) {
      // 未注册处理器的历史节点：仅回写步骤标记，便于扩展时再挂策略
      log.warn("热点节点无处理器，跳过业务执行 typeKey={}", typeKey);
      Map<String, Object> skipped = new LinkedHashMap<>();
      skipped.put(HotNewsGraphStateKeys.HOT_NEWS_STEP, typeKey);
      return skipped;
    }
    log.info("热点图节点分发 typeKey={} nodeKey={}", typeKey, nodeKey);
    HotNewsGraphState graphState = HotNewsGraphState.fromMap(state);
    List<SkillPromptBindingView> bindings = toPromptBindings(skillBindings);
    // 策略执行并合并状态
    handler.execute(graphState, bindings, nodeKey, nodeName);
    Map<String, Object> output = graphState.toMap();
    output.put(HotNewsGraphStateKeys.HOT_NEWS_STEP, nodeType.getValue());
    if (nodeType == HotNewsWorkflowNodeType.GENERATE_DRAFT && graphState.getDraft() != null) {
      output.put(nodeType.getValue(), graphState.getDraft());
    }
    if (nodeType == HotNewsWorkflowNodeType.REVIEW_DRAFT && graphState.getDraft() != null) {
      output.put(nodeType.getValue(), graphState.getDraft());
    }
    if (nodeType == HotNewsWorkflowNodeType.GENERATE_WECHAT && graphState.getWechat() != null) {
      output.put(nodeType.getValue(), graphState.getWechat());
    }
    return output;
  }

  /**
   * 将快照中的绑定 Map 转为类型化绑定视图。
   *
   * @param skillBindings 原始绑定列表
   * @return 绑定视图列表
   */
  private static List<SkillPromptBindingView> toPromptBindings(List<Map<String, Object>> skillBindings) {
    if (skillBindings == null || skillBindings.isEmpty()) {
      return List.of();
    }
    List<SkillPromptBindingView> result = new ArrayList<>();
    for (Map<String, Object> binding : skillBindings) {
      if (binding == null) {
        continue;
      }
      Object priority = binding.get("priority");
      result.add(SkillPromptBindingView.builder()
          .skillId(binding.get("skillId") == null ? null : String.valueOf(binding.get("skillId")))
          .priority(priority instanceof Number number ? number.intValue() : 100)
          .enabled(binding.get("enabled") == null || Boolean.TRUE.equals(binding.get("enabled")))
          .build());
    }
    return result;
  }
}
