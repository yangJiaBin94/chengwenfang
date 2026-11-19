package com.chengwenfang.sourcing.facade;

import com.chengwenfang.sourcing.facade.dto.view.SourceChannelCatalogView;
import java.util.List;

/**
 * 信源沉淀池只读门面。
 */
public interface SourceChannelCatalogFacade {

  /**
   * 列出全部启用的沉淀池条目（按 sort_order、code 排序）。
   *
   * @return 启用条目；无则空列表
   */
  List<SourceChannelCatalogView> listEnabledCatalog();
}
