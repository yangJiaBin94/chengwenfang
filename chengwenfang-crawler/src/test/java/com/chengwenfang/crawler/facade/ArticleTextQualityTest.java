package com.chengwenfang.crawler.facade;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** {@link ArticleTextQuality} 导航噪声与信息不足判定。 */
class ArticleTextQualityTest {

  @Test
  void navigation_noise_detects_markdown_menu() {
    assertTrue(ArticleTextQuality.looksLikeNavigationNoise(
        "[财经](http://a.com) * [焦点](http://b.com) * [股票](http://c.com)"));
    assertFalse(ArticleTextQuality.looksLikeNavigationNoise(
        "11月3日，皇冠新材上市首日开盘大涨，涨幅最高达288.69%。"));
  }

  @Test
  void navigation_noise_keeps_article_with_inline_quote_links() {
    // 东方财富 Lightpanda 正文：多句事实 + 多个行情链，不得当导航拒掉
    String body = """
        11月3日，[N皇冠](http://quote.eastmoney.com/unify/r/0.001381)新材上市首日开盘即大幅上涨。\
        截至发稿，该股大涨169.79%，报62.88元/股，盘中最高触及66元/股。\
        产品已进入[苹果](http://quote.eastmoney.com/unify/r/105.AAPL)、\
        [特斯拉](http://quote.eastmoney.com/unify/r/105.TSLA)、\
        [宁德时代](http://quote.eastmoney.com/unify/r/0.300750)等终端企业中的应用。\
        2023年至2025年，营业收入分别为28.95亿元、31.59亿元、34.08亿元。\
        """;
    assertFalse(ArticleTextQuality.looksLikeNavigationNoise(body));
  }

  @Test
  void insufficient_brief_detects_placeholder() {
    assertTrue(ArticleTextQuality.looksLikeInsufficientBrief(
        "信息不足，无法形成可靠摘要（正文仅为网站导航链接，除标题外无可核对的具体事实）。"));
    assertFalse(ArticleTextQuality.looksLikeInsufficientBrief(
        "皇冠新材上市首日开盘大涨，发行价16.98元。"));
  }
}
