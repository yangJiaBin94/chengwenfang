package com.chengwenfang.mode.hotnews.facade.constant;

/**
 * 热点主题自动配源结果键前缀。
 */
public final class HotNewsChannelAutoWireKeys {
  private HotNewsChannelAutoWireKeys() {}

  /** 沉淀池克隆键前缀，后接 catalog code。 */
  public static final String CATALOG_PREFIX = "catalog:";
  /** 沉淀池软删键前缀，后接归一化 base_url。 */
  public static final String CATALOG_DROP_PREFIX = "catalog-drop:";
}
