package com.chengwenfang.knowledge.application.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 知识库 Agent 工具注册表（allowlist）。
 */
@Component
public class KnowledgeAgentToolRegistry {

  /** name → 工具。 */
  private final Map<String, KnowledgeAgentTool> toolsByName = new LinkedHashMap<>();

  /**
   * @param tools Spring 注入的工具列表
   */
  public KnowledgeAgentToolRegistry(List<KnowledgeAgentTool> tools) {
    if (CollUtil.isNotEmpty(tools)) {
      for (KnowledgeAgentTool tool : tools) {
        if (tool != null && StrUtil.isNotBlank(tool.name())) {
          toolsByName.put(tool.name(), tool);
        }
      }
    }
  }

  /**
   * 是否已注册。
   *
   * @param name 工具名
   * @return 是否存在
   */
  public boolean has(String name) {
    return toolsByName.containsKey(name);
  }

  /**
   * 执行 allowlist 内工具；未知名拒绝。
   *
   * @param name 工具名
   * @param args 参数
   * @return 结果；未注册返回错误摘要
   */
  public String execute(String name, String args) {
    KnowledgeAgentTool tool = toolsByName.get(name);
    if (tool == null) {
      return "tool_not_allowed:" + StrUtil.nullToEmpty(name);
    }
    return tool.execute(args);
  }

  /**
   * 已注册工具名列表。
   *
   * @return 名称
   */
  public List<String> names() {
    return List.copyOf(toolsByName.keySet());
  }
}
