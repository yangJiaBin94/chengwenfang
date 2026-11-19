package com.chengwenfang.knowledge.application.agent;

/**
 * 知识库 Agent 只读工具接口。
 */
public interface KnowledgeAgentTool {

  /**
   * 工具名（闭集）。
   *
   * @return 名称
   */
  String name();

  /**
   * 执行工具。
   *
   * @param args 参数摘要（可空）
   * @return 结果摘要
   */
  String execute(String args);
}
