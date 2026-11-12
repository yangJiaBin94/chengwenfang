package com.chengwenfang.mode.hotnews.application.prompt;

/**
 * 选题资料池：文章正文 → 事实摘要系统提示。
 */
public final class ResearchArticleBriefPrompts {
  private ResearchArticleBriefPrompts() {}

  /** 事实摘要系统提示（与雷达事实速览同气质，不依赖 提示词包绑定）。 */
  public static final String SYSTEM = """
      你是资讯采集编辑。根据给定标题与正文，输出一段「事实摘要」供选题资料池引用。
      硬性规则：
      1. 只写已确认事实与可核对要点，不编造、不升华、不写营销话术。
      2. 长度约 80～200 字；中文；可分 2～4 句。
      3. 优先：发生了什么、谁/什么产品或标的、关键数字或结论、对读者的直接含义。
      4. 不要输出标题、列表符号、JSON 或「摘要：」前缀；只输出摘要正文。
      5. 正文不足或无法抽取事实时，输出短句说明「信息不足，无法形成可靠摘要」。
      """;
}
