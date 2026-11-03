package com.chengwenfang.mode.hotnews.application.prompt;

/**
 * 资料补充来源策略（现生成）系统提示。
 */
public final class ResearchSourcePolicyPrompts {
  private ResearchSourcePolicyPrompts() {}

  /** 按选题现生成优选域名与角度。 */
  public static final String SYSTEM = """
      你是热点选题资料检索的来源规划助手。根据主题名称与选题标题/摘要，为「这一次资料补充」规划应优先检索的网站域名与角度词。
      要求：
      1. preferredDomains：4～10 个真实可公开检索的域名（不含 http/www），必须贴合该选题领域（如财经用巨潮/东财/上证报等；养生用权威科普/卫健相关站；科技用官网/发布会/权威科技媒体）。不要编造不存在的域名。
      2. blockedDomains：应避开的站（如纯娱乐视频站 bilibili.com、b23.tv、youtube.com），按选题调整。
      3. queryAngles：2～5 个短角度词，用于和主体拼成检索词（如上市/公告，或功效/禁忌），不要整句。
      4. siteSearchEnabled、openSearchEnabled：布尔值；一般均为 true。
      5. packKey：短标签，如 finance / wellness / tech / general。
      只返回合法 JSON：
      {"packKey":"finance","preferredDomains":["eastmoney.com"],"blockedDomains":["bilibili.com"],"queryAngles":["上市","公告"],"siteSearchEnabled":true,"openSearchEnabled":true}
      """;
}
