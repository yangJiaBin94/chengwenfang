package com.chengwenfang.prompt.facade.builtin;

/**
 * 内置配置体检问题域（结构化，避免按中文文案分类）。
 */
public enum BuiltinIssueKind {
  /** 提示词包 缺失或不可用。 */
  PROMPT_PACK,
  /** 工作流缺失、快照损坏或节点/绑定不完整。 */
  WORKFLOW
}
