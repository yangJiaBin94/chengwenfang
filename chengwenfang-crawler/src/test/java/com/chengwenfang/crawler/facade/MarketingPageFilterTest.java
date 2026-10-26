package com.chengwenfang.crawler.facade;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MarketingPageFilterTest {
  @Test
  void skips_vendor_homes_and_product_landings() {
    assertTrue(MarketingPageFilter.shouldSkip("https://www.deepseek.com/zh", "DeepSeek | 深度求索"));
    assertTrue(MarketingPageFilter.shouldSkip("https://www.deepseek.com/", "DeepSeek | Into the Unknown"));
    assertTrue(MarketingPageFilter.shouldSkip("https://chat.deepseek.com/", "DeepSeek"));
    assertTrue(MarketingPageFilter.shouldSkip("https://platform.deepseek.com/", "DeepSeek Platform"));
    assertTrue(MarketingPageFilter.shouldSkip(
        "https://www.aliyun.com/product/tongyi",
        "千问大模型_AI大模型_一站式大模型推理和部署服务-阿里云"));
    assertTrue(MarketingPageFilter.shouldSkip("https://openai.com/chatgpt", "ChatGPT"));
  }

  @Test
  void skips_news_portal_listing_pages() {
    assertTrue(MarketingPageFilter.shouldSkip(
        "https://www.aizws.net/news",
        "AI资讯 - 每日人工智能新闻与行业动态 | AI 中文社区"));
    assertTrue(MarketingPageFilter.shouldSkip(
        "https://www.mergeek.com/ai_news",
        "AI News - 最新人工智能行业资讯"));
    assertTrue(MarketingPageFilter.shouldSkip(
        "https://www.mergeek.com/zh/ai_news",
        "AI News - 最新人工智能行业资讯"));
    assertTrue(MarketingPageFilter.looksLikeNewsPortalTitle(
        "每日更新AI资讯与人工智能新闻,聚焦大模型"));
  }

  @Test
  void skips_ecommerce_product_pages_and_titles() {
    assertTrue(MarketingPageFilter.shouldSkip(
        "https://item.jd.com/100012345678.html",
        "钙尔奇软糖软骨素钙片603送长辈礼盒装 中老年关节维生素D补钙维骨力"));
    assertTrue(MarketingPageFilter.shouldSkip(
        "https://item.jd.com/100098765432.html",
        "健力多汤臣倍健氨糖软骨素钙片180片 中老年成人护关节 补钙片 京东自营"));
    assertTrue(MarketingPageFilter.shouldSkip(
        "https://detail.tmall.com/item.htm?id=1",
        "某品牌氨糖软骨素钙片 天猫旗舰店"));
    assertTrue(MarketingPageFilter.shouldSkip(
        "https://www.smzdm.com/p/123456/",
        "钙片礼盒装 券后价"));
    assertTrue(MarketingPageFilter.looksLikeCommerceProductTitle(
        "健力多汤臣倍健氨糖软骨素钙片180片 中老年成人护关节 补钙片 京东自营"));
    // 无商城域名、仅靠推销标题也应拦截（搜索摘要常如此）
    assertTrue(MarketingPageFilter.shouldSkip(
        "https://www.example-news.com/article/1",
        "钙尔奇软糖软骨素钙片603送长辈礼盒装 中老年关节维生素D补钙维骨力"));
    assertTrue(MarketingPageFilter.shouldSkip(
        "https://www.example-news.com/a/2",
        "足力健老人鞋2026秋季新款轻便软底中老年健步鞋爸爸妈妈休闲运动"));
    assertTrue(MarketingPageFilter.shouldSkip(
        "https://www.example-news.com/a/3",
        "宝宝补钙别乱选，买对才是关键"));
    assertTrue(MarketingPageFilter.shouldSkip(
        "https://www.example-news.com/a/4",
        "#中年学员减脂进行时，#每日体重实拍 #中老年减肥 #中老年减脂训练营"));
    assertTrue(MarketingPageFilter.shouldSkip(
        "https://www.bilibili.com/video/BV1xx411c7mD",
        "每天2次，氨糖+钙+软骨素，关节越来越年轻！赶紧试试"));
  }

  @Test
  void keeps_vendor_news_and_third_party_articles() {
    assertFalse(MarketingPageFilter.shouldSkip(
        "https://openai.com/index/introducing-gpt-5/", "Introducing GPT-5"));
    assertFalse(MarketingPageFilter.shouldSkip(
        "https://api-docs.deepseek.com/zh-cn/updates/changelog-2026/", "更新日志 - DeepSeek API Docs"));
    assertFalse(MarketingPageFilter.shouldSkip(
        "https://techcrunch.com/2026/09/24/openai-news/", "OpenAI announces something"));
    assertFalse(MarketingPageFilter.shouldSkip(
        "https://www.36kr.com/p/123456", "DeepSeek 发布新模型"));
    assertFalse(MarketingPageFilter.shouldSkip(
        "https://www.36kr.com/p/999",
        "中老年关节保养：医生提醒别只靠补剂"));
    assertFalse(MarketingPageFilter.shouldSkip(
        "https://www.thepaper.cn/newsDetail_forward_1",
        "专家提醒：中老年补钙不能只看广告"));
  }
}
