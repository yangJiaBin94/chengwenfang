package com.workbench.skill.facade.builtin;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * 一条内置配置体检问题。
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor
public final class BuiltinIssue {
  /** 问题域。 */
  private final BuiltinIssueKind kind;
  /** 面向运维的中文描述（不含提示词正文）。 */
  private final String message;

  /**
   * 是否为工作流域问题。
   *
   * @return true 表示 WORKFLOW
   */
  public boolean isWorkflow() {
    return kind == BuiltinIssueKind.WORKFLOW;
  }
}
