package com.workbench.crawler.infrastructure.fetch;

import com.workbench.crawler.facade.dto.view.FetchedItemView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 按 URL（缺省用标题）去重合并抓取条目，保持插入顺序。
 */
public final class FetchedItemMerger {
  private final LinkedHashMap<String, FetchedItemView> byKey = new LinkedHashMap<>();

  /**
   * 若键尚未存在则加入。
   *
   * @param item 条目
   */
  public void offer(FetchedItemView item) {
    if (item == null) {
      return;
    }
    String key = item.getUrl() == null || item.getUrl().isBlank() ? item.getTitle() : item.getUrl();
    if (key == null || key.isBlank() || byKey.containsKey(key)) {
      return;
    }
    byKey.put(key, item);
  }

  /**
   * @return 去重后的有序列表
   */
  public List<FetchedItemView> toList() {
    return new ArrayList<>(byKey.values());
  }
}
