package com.chengwenfang.knowledge.facade.spi;

import com.chengwenfang.knowledge.domain.model.KnowledgeVectorHit;
import com.chengwenfang.knowledge.domain.model.KnowledgeVectorUpsert;
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
   * 向量相似度检索。
   *
   * @param queryVector 查询向量
   * @param limit       返回条数上限
   * @return 命中列表（按相似度降序）
   */
  List<KnowledgeVectorHit> search(float[] queryVector, int limit);

  /**
   * 删除某文档下的全部向量点。
   *
   * @param documentId 文档 ID
   */
  void deleteByDocumentId(String documentId);
}
