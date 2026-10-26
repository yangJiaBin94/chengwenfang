package com.workbench.crawler.facade;

import com.workbench.crawler.facade.dto.request.ArticleContentRequest;
import com.workbench.crawler.facade.dto.view.ArticleContentResult;
import com.workbench.crawler.facade.dto.view.UrlReachabilityView;

/**
 * 文章正文获取门面：Jina → Trafilatura → 搜索摘要；兼轻量直连探测。
 */
public interface ArticleContentFacade {
  /**
   * 解析文章正文（按配置瀑布降级）。
   *
   * @param request 请求
   * @return 结果；永不抛业务异常到调用方
   */
  ArticleContentResult resolve(ArticleContentRequest request);

  /**
   * 直连探测 URL 是否可打开（非 Jina 轻量 HEAD/GET）。
   *
   * @param url 目标 URL
   * @return 可达性视图
   */
  UrlReachabilityView probeUrlReachability(String url);

  /**
   * 下载源站 HTML 并抽出日期探测用纯文本（Jina 抽空后的 Pass2）。
   *
   * @param url 目标 URL
   * @return 去标签文本；失败返回空串
   */
  String fetchRawHtmlDateInputText(String url);
}
