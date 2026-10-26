package com.chengwenfang.skill.facade.constant;

/**
 * 工作流图结构/控制类节点 typeKey（课程与通用控制；热点专用见 {@link com.chengwenfang.skill.facade.enums.HotNewsWorkflowNodeType}）。
 */
public final class WorkflowStructuralNodeKeys {
  private WorkflowStructuralNodeKeys() {}

  /** 开始节点。 */
  public static final String START = "start";
  /** 结束节点。 */
  public static final String END = "end";
  /** 课程提交版本。 */
  public static final String COMMIT_VERSION = "commit_version";
  /** 有界循环。 */
  public static final String BOUNDED_LOOP = "bounded_loop";
  /** 条件分支。 */
  public static final String CONTROLLED_CONDITION = "controlled_condition";
  /** 课程封面图。 */
  public static final String IMAGE_COVER = "image_cover";
  /** 课程正文插图。 */
  public static final String IMAGE_INLINE = "image_inline";
  /** 最终一致性检查（课程）。 */
  public static final String FINAL_VALIDATION = "final_validation";
  /** 公式格式检查。 */
  public static final String FORMULA_STYLE_VALIDATION = "formula_style_validation";
  /** 公式可读性检查。 */
  public static final String FORMULA_EXPRESSION_VALIDATION = "formula_expression_validation";
  /** 通用 LLM 文本。 */
  public static final String LLM_TEXT = "llm_text";
  /** 公众号转换（课程）。 */
  public static final String WECHAT_CONVERSION = "wechat_conversion";
  /** 自定义 AI 节点（提示词 + 白名单结构化动作）。 */
  public static final String CUSTOM_AI = "custom_ai";

  /**
   * 是否为自定义 AI 节点 typeKey。
   *
   * @param typeKey 节点类型键
   * @return 匹配则为 true
   */
  public static boolean isCustomAi(String typeKey) {
    return CUSTOM_AI.equals(typeKey);
  }
}
