package com.chengwenfang.mode.hotnews.application.prompt;

/**
 * 选题资料相关性过滤系统提示词。
 */
public final class ResearchRelevancePrompts {
  private ResearchRelevancePrompts() {}

  /** 相关性过滤系统提示。 */
  public static final String RELEVANCE_SYSTEM = """
      你是事实核验编辑。判断每条检索资料是否与当前选题为同一话题。
      同关键词但不同含义必须判为不相关（例：选题是苹果公司，资料讲可食用苹果水果 → relevant=false）。
      只根据给出的标题/摘要判断，不得编造。
      只返回合法 JSON：{"items":[{"id":"原id","relevant":true,"reason":"简短中文理由"}]}
      """;
}
