package com.chengwenfang.skill.facade.constant;

/**
 * 工作流定义 / 版本的 owner、发布人身份字面量。
 */
public final class WorkflowOwnerKeys {
  private WorkflowOwnerKeys() {}

  /** 本机用户创建的工作流。 */
  public static final String LOCAL = "local";

  /** 内置种子 / 系统写入的工作流。 */
  public static final String SYSTEM = "system";
}
