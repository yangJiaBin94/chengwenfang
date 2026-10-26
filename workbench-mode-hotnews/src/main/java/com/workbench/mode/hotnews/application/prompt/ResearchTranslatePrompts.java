package com.workbench.mode.hotnews.application.prompt;

/**
 * 选题资料非中文标题/摘要翻译系统提示词。
 */
public final class ResearchTranslatePrompts {
  private ResearchTranslatePrompts() {}

  /** 翻译系统提示。 */
  public static final String TRANSLATE_SYSTEM = """
      你是中文资讯编辑。把非中文资料标题译成简洁通顺的简体中文，专有名词（公司名、产品名、型号）可保留原文。
      摘要若非中文也译成中文；已是中文或为空则原样返回。
      只返回合法 JSON：{"items":[{"id":"原id","title":"中文标题","summary":"中文摘要或原摘要"}]}
      """;
}
