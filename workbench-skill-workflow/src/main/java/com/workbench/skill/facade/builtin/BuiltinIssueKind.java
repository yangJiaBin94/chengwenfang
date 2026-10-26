package com.workbench.skill.facade.builtin;

/**
 * 内置配置体检问题域（结构化，避免按中文文案分类）。
 */
public enum BuiltinIssueKind {
  /** Skill 缺失或不可用。 */
  SKILL,
  /** 工作流缺失、快照损坏或节点/绑定不完整。 */
  WORKFLOW
}
