package com.chengwenfang.sourcing.application;

import com.chengwenfang.sourcing.facade.SourceChannelCatalogFacade;
import com.chengwenfang.sourcing.facade.dto.view.SourceChannelCatalogView;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * {@link SourceChannelCatalogFacade} 实现。
 */
@Service
@RequiredArgsConstructor
public class SourceChannelCatalogFacadeImpl implements SourceChannelCatalogFacade {
  /** 沉淀池查询。 */
  private final SourceChannelCatalogService catalogService;

  @Override
  public List<SourceChannelCatalogView> listEnabledCatalog() {
    return catalogService.listEnabledCatalog();
  }
}
