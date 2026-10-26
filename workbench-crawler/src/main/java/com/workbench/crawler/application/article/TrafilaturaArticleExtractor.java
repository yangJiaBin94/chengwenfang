package com.workbench.crawler.application.article;

/**
 * Trafilatura 正文抽取接缝（测试中可替换实现）。
 */
public interface TrafilaturaArticleExtractor {
  /**
   * 抽取正文与元数据。
   *
   * @param url 文章地址
   * @return CLI 结果；失败时 ok=false
   */
  TrafilaturaCliResult extract(String url);
}
