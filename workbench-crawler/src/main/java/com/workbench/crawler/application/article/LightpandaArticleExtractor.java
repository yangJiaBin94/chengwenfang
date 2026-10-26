package com.workbench.crawler.application.article;

/**
 * Lightpanda 浏览器渲染抽取接缝（测试中可替换实现）。
 */
public interface LightpandaArticleExtractor {
  /**
   * 经浏览器渲染后抽取正文。
   *
   * @param url 文章地址
   * @return CLI 结果；未启用或失败时 ok=false
   */
  TrafilaturaCliResult extract(String url);
}