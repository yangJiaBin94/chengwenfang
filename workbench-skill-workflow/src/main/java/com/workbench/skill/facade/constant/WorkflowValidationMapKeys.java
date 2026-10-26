package com.workbench.skill.facade.constant;

/**
 * 工作流校验结果 JSON / Map 字段名。
 */
public final class WorkflowValidationMapKeys {
  private WorkflowValidationMapKeys() {}

  /** 是否通过校验。 */
  public static final String VALID = "valid";
  /** 错误列表。 */
  public static final String ERRORS = "errors";
  /** 警告列表。 */
  public static final String WARNINGS = "warnings";
  /** 校验时间。 */
  public static final String CHECKED_AT = "checkedAt";
  /** 定义内容哈希（可空）。 */
  public static final String DEFINITION_HASH = "definitionHash";
}
