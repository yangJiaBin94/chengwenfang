package com.chengwenfang.knowledge.facade.constant;

/**
 * 知识图谱相关阈值。
 */
public final class KnowledgeGraphConstants {

  /** 低于该置信度的边默认禁用。 */
  public static final double LOW_CONFIDENCE = 0.45;

  /** 索引流水线写入提及边的默认置信度（高于 {@link #LOW_CONFIDENCE} 故默认启用）。 */
  public static final double DEFAULT_MENTION_CONFIDENCE = 0.55;

  /** 提及边业务 UID 前缀。 */
  public static final String MENTIONS_EDGE_UID_PREFIX = "mentions:";

  private KnowledgeGraphConstants() {}

  /**
   * 由切块 ID 推导提及边 UID。
   *
   * @param chunkId 切块 ID
   * @return 边 UID
   */
  public static String mentionsEdgeUid(String chunkId) {
    return MENTIONS_EDGE_UID_PREFIX + chunkId;
  }
}
