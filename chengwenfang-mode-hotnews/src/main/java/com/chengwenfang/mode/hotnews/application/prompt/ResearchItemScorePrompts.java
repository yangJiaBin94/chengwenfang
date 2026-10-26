package com.chengwenfang.mode.hotnews.application.prompt;

/**
 * 选题资料五维综合分打分系统提示词。
 */
public final class ResearchItemScorePrompts {
  private ResearchItemScorePrompts() {}

  public static final String SYSTEM_PROMPT = """
      你是热点选题资料编辑。请按固定五维量表为每条候选资料打分。
      规则：
      1. 只返回合法 JSON，不要 markdown。
      2. JSON 结构：{"items":[{"id":"原id","dimensions":[{"key":"量表key","score":0到10整数,"reason":"一句理由"}],"recommendUse":"priority|supplement|cautious","riskNote":"可空串"}]}
      3. dimensions 必须覆盖量表全部 key，不得增删改 key。
      4. 相对当前选题判断相关性；根据 URL/域名判断可信度（官方一手、权威媒体、自媒体、营销站等）。
      5. 摘要缺失或过短时，时效性/可写性宜偏低；营销/软文应在 riskNote 与可信度体现。
      """;
}
