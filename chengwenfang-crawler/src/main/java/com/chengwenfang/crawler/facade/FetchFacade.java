package com.chengwenfang.crawler.facade;

import com.chengwenfang.crawler.facade.dto.request.FetchRequest;
import com.chengwenfang.crawler.facade.dto.view.FetchResult;
import java.util.List;

/**
 * 网页/RSS/GitHub 等源的抓取门面；含 SSRF 校验与按主机限流。
 */
public interface FetchFacade {
  /** 抓取单个渠道并按 type 走策略解析条目。
   *
   * @param request type：web / rss / github / searxng / tavily；hours 用于过滤过旧条目
   */
  FetchResult fetch(FetchRequest request);

  /** 并行批量抓取。 */
  List<FetchResult> fetchBatch(List<FetchRequest> requests);

  /** 连通性探测：成功时最多返回 3 条样本条目。 */
  FetchResult testConnection(FetchRequest request);
}
