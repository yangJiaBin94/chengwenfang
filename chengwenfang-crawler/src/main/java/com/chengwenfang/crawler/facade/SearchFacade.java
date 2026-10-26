package com.chengwenfang.crawler.facade;

import com.chengwenfang.crawler.facade.dto.view.SearchHitView;
import java.util.List;

/**
 * Web 搜索门面，返回去重后的标题/URL/摘要命中。
 */
public interface SearchFacade {
  /**
   * 按关键词搜索。
   *
   * @param query 查询词；空则返回空列表
   * @param limit 期望条数，实现侧通常限制在合理上限内
   */
  List<SearchHitView> search(String query, int limit);
}
