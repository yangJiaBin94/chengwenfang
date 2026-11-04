package com.chengwenfang.crawler.facade;

import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;

/**
 * 文章抽取文本质量：导航噪声与「信息不足」类摘要判定。
 */
public final class ArticleTextQuality {
  private ArticleTextQuality() {}

  /**
   * 文本是否像站点导航/菜单噪声（无句子的链接菜单；勿误杀正文内嵌行情链）。
   *
   * <p>东方财富等站正文常含多个 {@code [标的](quote...)} 链，但同时有多句「。」事实句；
   * 仅「链接数 ≥ 3」会误拒 Lightpanda 正文。导航摘要则几乎无句号。
   *
   * @param text 正文或摘要
   * @return 疑似导航时 true
   */
  public static boolean looksLikeNavigationNoise(String text) {
    if (StrUtil.isBlank(text)) {
      return false;
    }
    String trimmed = text.trim();
    int mdLinks = ReUtil.findAllGroup0("\\[[^\\]]{1,40}\\]\\(https?://[^)]+\\)", trimmed).size();
    int sentenceMarks =
        StrUtil.count(trimmed, '。') + StrUtil.count(trimmed, '！') + StrUtil.count(trimmed, '？');
    // 多 Markdown 链且几乎无完整中文句 → 菜单/目录
    if (mdLinks >= 3 && sentenceMarks == 0) {
      return true;
    }
    // 短文本里链接极密、句子很少 → 仍像导航条被浅抓进来
    if (mdLinks >= 5 && sentenceMarks <= 1 && trimmed.length() <= 800) {
      return true;
    }
    int separators =
        StrUtil.count(trimmed, '*') + StrUtil.count(trimmed, '|') + StrUtil.count(trimmed, '·');
    return sentenceMarks == 0 && separators >= 4 && trimmed.length() > 40;
  }

  /**
   * 摘要是否像模型承认「抽不到事实」的占位句（应触发兜底，不可入库当正文摘要）。
   *
   * @param summary 摘要
   * @return 信息不足类占位时 true
   */
  public static boolean looksLikeInsufficientBrief(String summary) {
    if (StrUtil.isBlank(summary)) {
      return false;
    }
    String text = summary.trim();
    return text.contains("信息不足")
        || text.contains("无法形成可靠摘要")
        || text.contains("仅为网站导航")
        || text.contains("除标题外无可核对");
  }

  /**
   * 抽取正文是否应视为不可用（过短、导航噪声）。
   *
   * @param text 抽取文本
   * @return 应继续瀑布降级时 true
   */
  public static boolean shouldRejectExtractedBody(String text) {
    return StrUtil.isBlank(text) || looksLikeNavigationNoise(text);
  }
}
