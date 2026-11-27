package com.chengwenfang.knowledge.facade.spi;

import java.util.List;

/** 知识库向量存储 SPI：切块向量写入与相似度检索。 */
public interface KnowledgeVectorStore {

  /**
   * 确保集合维度匹配（必要时重建内存索引等）。
   *
   * @param dimension 向量维度
   */
  void ensureCollection(int dimension);

  /**
   * 批量 upsert 向量。
   *
   * @param items 向量记录
   */
  void upsert(List<KnowledgeVectorUpsert> items);

  /**
   * 向量相似度检索（无过滤）。
   *
   * @param queryVector 查询向量
   * @param limit       返回条数上限
   * @return 命中列表（按相似度降序）
   */
  default List<KnowledgeVectorHit> search(float[] queryVector, int limit) {
    return search(queryVector, limit, KnowledgeVectorFilter.builder().build());
  }

  /**
   * 向量相似度检索（检索侧过滤）。
   *
   * @param queryVector 查询向量
   * @param limit       返回条数上限
   * @param filter      元数据过滤
   * @return 命中列表
   */
  List<KnowledgeVectorHit> search(float[] queryVector, int limit, KnowledgeVectorFilter filter);

  /**
   * 删除某文档下全部版本的向量点（对账清孤儿用）。
   *
   * @param documentId 文档 ID
   */
  void deleteByDocumentId(String documentId);

  /**
   * 删除某文档在指定索引版本下的向量点（蓝绿写新不毁旧）。
   *
   * @param documentId   文档 ID
   * @param indexVersion 索引版本；空则等同 {@link #deleteByDocumentId(String)}
   */
  void deleteByDocumentId(String documentId, String indexVersion);

  /**
   * 按切块 ID 删除向量点（先 upsert 新点再删旧点，避免失败留下空洞）。
   *
   * @param chunkIds 切块 / 点 ID 列表
   */
  void deleteByChunkIds(List<String> chunkIds);

  /**
   * 删除指定索引版本 + 所属用户下的向量点（蓝绿切流后 GC 旧版）。
   *
   * @param indexVersion 索引版本
   * @param ownerUserId  所属用户；空则拒绝（避免误删全库）
   */
  void deleteByIndexVersionAndOwner(String indexVersion, String ownerUserId);

  /**
   * 是否为降级实现（如内存库）。
   *
   * @return 降级则为 true
   */
  default boolean degraded() {
    return false;
  }
}
