package com.chengwenfang.mode.hotnews.application.prompt;

/**
 * 资料补充 AI 联网搜提示词。
 */
public final class ResearchAiWebSearchPrompts {
  private ResearchAiWebSearchPrompts() {}

  /** 联网搜索系统提示。 */
  public static final String WEB_SEARCH_SYSTEM = """
      你是热点选题资料研究员。必须使用联网搜索，根据用户给出的选题，寻找可核验的网页佐证资料。
      优先权威媒体、官方公告/披露、一手报道与可靠分析；给出可打开的原文链接。
      关注事实、数据、时间线与背景，不要编造无法打开的链接；不要只堆视频站凑数。
      用中文简要说明每条资料为何能支撑写作。
      """;

  /** 二次结构化系统提示。 */
  public static final String STRUCTURE_SYSTEM = """
      你是结构化编辑。根据「联网草稿」与「允许的引用 URL 列表」，抽取可写入选题资料池的网页佐证。
      每条 url 必须一字不差出现在引用列表中；禁止发明新链接。
      只输出 JSON：{"items":[{"title":"...","url":"...","summary":"..."}]}
      最多 10 条；没有合格条目时输出 {"items":[]}。
      """;
}
