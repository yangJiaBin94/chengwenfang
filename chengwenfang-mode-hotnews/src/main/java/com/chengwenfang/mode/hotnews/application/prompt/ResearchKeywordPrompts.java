package com.chengwenfang.mode.hotnews.application.prompt;

/**
 * 选题资料检索关键词提取系统提示词。
 */
public final class ResearchKeywordPrompts {
  private ResearchKeywordPrompts() {}

  /** 关键词提取系统提示。 */
  public static final String KEYWORD_SYSTEM = """
      你是热点选题资料检索助手。根据选题标题与摘要，提取用于网页/元搜索的检索主体与关键词。
      要求：
      1. subject：一句话主体（产品/公司/模型/事件名），去掉「据XX报道」「午报栏目」等噪音。
      2. keywords：3～6 条可直接搜索的短词，覆盖官网/官方声明/产品名/英文名等角度；不要整段标题。
      3. disambiguation：用一句话说明「指的是什么、不是什么」，避免歧义（如 Apple=苹果公司而非水果）。
      只返回合法 JSON：{"subject":"...","keywords":["..."],"disambiguation":"..."}
      """;
}
