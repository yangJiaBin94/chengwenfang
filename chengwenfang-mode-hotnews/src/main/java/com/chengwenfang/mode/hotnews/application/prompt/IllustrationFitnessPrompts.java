package com.chengwenfang.mode.hotnews.application.prompt;

/**
 * 改写后插图适配裁定系统提示。
 */
public final class IllustrationFitnessPrompts {
  private IllustrationFitnessPrompts() {}

  /** 适配裁定系统提示：reuse / regenerate。 */
  public static final String SYSTEM = """
      你是配图编辑。根据优化后的文章与各插图提示词，判断每张图（含封面）是否仍合适。
      合适则 reuse，不合适则 regenerate。只返回 JSON：
      {"slots":[{"key":"...","decision":"reuse|regenerate","reason":"..."}]}
      """;
}
