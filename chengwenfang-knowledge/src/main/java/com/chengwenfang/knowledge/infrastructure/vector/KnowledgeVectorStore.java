package com.chengwenfang.knowledge.infrastructure.vector;

import java.util.List;

/**
 * 知识库向量存储抽象：upsert、按文档删除、相似检索与健康检查。
 */
public interface KnowledgeVectorStore {

  /**
   * 批量写入或覆盖向量点。
   *
   * @param points 待写入点列表
   */
  void upsert(List<KnowledgeVectorPoint> points);

  /**
   * 按文档 ID 删除该文档下全部向量点。
   *
   * @param documentId 文档 ID
   */
  void deleteByDocumentId(String documentId);

  /**
   * 相似向量检索。
   *
   * @param query  查询向量
   * @param topK   返回条数上限
   * @param filter 可选元数据过滤
   * @return 命中列表，按得分降序
   */
  List<KnowledgeVectorMatch> search(float[] query, int topK, KnowledgeVectorFilter filter);

  /**
   * 确保集合存在且向量维度匹配（不存在则创建）。
   *
   * @param dimension 向量维度
   */
  void ensureCollection(int dimension);

  /**
   * 存储后端是否可用。
   *
   * @return true 表示健康
   */
  boolean healthy();
}
