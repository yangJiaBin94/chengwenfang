package com.chengwenfang.knowledge.infrastructure.vector;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.knowledge.facade.spi.KnowledgeVectorFilter;
import com.chengwenfang.knowledge.facade.spi.KnowledgeVectorHit;
import com.chengwenfang.knowledge.facade.spi.KnowledgeVectorStore;
import com.chengwenfang.knowledge.facade.spi.KnowledgeVectorUpsert;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存向量存储：用于 Qdrant 未启用或集成测试；基于余弦相似度检索。
 */
public class InMemoryKnowledgeVectorStore implements KnowledgeVectorStore {

  /** 切块 ID → 向量记录。 */
  private final ConcurrentHashMap<String, KnowledgeVectorUpsert> points = new ConcurrentHashMap<>();

  /** {@inheritDoc} */
  @Override
  public void upsert(List<KnowledgeVectorUpsert> items) {
    if (CollUtil.isEmpty(items)) {
      return;
    }
    for (KnowledgeVectorUpsert item : items) {
      if (item == null || StrUtil.isBlank(item.getChunkId())) {
        continue;
      }
      points.put(item.getChunkId(), item);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void deleteByDocumentId(String documentId) {
    deleteByDocumentId(documentId, null);
  }

  /** {@inheritDoc} */
  @Override
  public void deleteByDocumentId(String documentId, String indexVersion) {
    if (StrUtil.isBlank(documentId)) {
      return;
    }
    points.entrySet().removeIf(entry -> {
      KnowledgeVectorUpsert point = entry.getValue();
      if (!documentId.equals(point.getDocumentId())) {
        return false;
      }
      if (StrUtil.isBlank(indexVersion)) {
        return true;
      }
      return indexVersion.equals(point.getIndexVersion());
    });
  }

  /** {@inheritDoc} */
  @Override
  public void deleteByChunkIds(List<String> chunkIds) {
    if (CollUtil.isEmpty(chunkIds)) {
      return;
    }
    for (String chunkId : chunkIds) {
      if (StrUtil.isNotBlank(chunkId)) {
        points.remove(chunkId);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public List<KnowledgeVectorHit> search(float[] queryVector, int limit, KnowledgeVectorFilter filter) {
    if (queryVector == null || queryVector.length == 0 || limit <= 0) {
      return List.of();
    }
    List<KnowledgeVectorHit> hits = new ArrayList<>();
    for (KnowledgeVectorUpsert point : points.values()) {
      if (!matchesFilter(point, filter)) {
        continue;
      }
      if (point.getVector() == null || point.getVector().length == 0) {
        continue;
      }
      double score = cosineSimilarity(queryVector, point.getVector());
      hits.add(KnowledgeVectorHit.builder()
          .chunkId(point.getChunkId())
          .documentId(point.getDocumentId())
          .score(score)
          .build());
    }
    hits.sort(Comparator.comparingDouble(KnowledgeVectorHit::getScore).reversed());
    if (hits.size() > limit) {
      return new ArrayList<>(hits.subList(0, limit));
    }
    return hits;
  }

  /** {@inheritDoc} */
  @Override
  public void ensureCollection(int dimension) {
    // 内存实现无需预创建集合
  }

  /** {@inheritDoc} */
  @Override
  public boolean degraded() {
    return true;
  }

  /**
   * 判断点是否满足检索过滤条件。
   *
   * @param point  向量记录
   * @param filter 过滤条件
   * @return 是否命中过滤
   */
  private boolean matchesFilter(KnowledgeVectorUpsert point, KnowledgeVectorFilter filter) {
    if (filter == null) {
      return true;
    }
    if (StrUtil.isNotBlank(filter.getDocumentId())
        && !filter.getDocumentId().equals(point.getDocumentId())) {
      return false;
    }
    if (!matchesAnyKind(
        point.getSourceKind(), filter.getSourceKinds(), filter.getSourceKind())) {
      return false;
    }
    if (!matchesAnyKind(point.getMediaKind(), filter.getMediaKinds(), filter.getMediaKind())) {
      return false;
    }
    if (StrUtil.isNotBlank(filter.getThemeId()) && !filter.getThemeId().equals(point.getThemeId())) {
      return false;
    }
    if (StrUtil.isNotBlank(filter.getIndexVersion())
        && !filter.getIndexVersion().equals(point.getIndexVersion())) {
      return false;
    }
    if (StrUtil.isNotBlank(filter.getEmbeddingModel())
        && !filter.getEmbeddingModel().equals(point.getEmbeddingModel())) {
      return false;
    }
    return true;
  }

  /**
   * 多选优先；否则单值；皆空则通过。
   *
   * @param actual 实际值
   * @param kinds  多选
   * @param single 单值
   * @return 是否匹配
   */
  private static boolean matchesAnyKind(String actual, List<String> kinds, String single) {
    if (CollUtil.isNotEmpty(kinds)) {
      List<String> allowed = kinds.stream().filter(StrUtil::isNotBlank).toList();
      if (allowed.isEmpty()) {
        return true;
      }
      return allowed.contains(actual);
    }
    if (StrUtil.isNotBlank(single)) {
      return single.equals(actual);
    }
    return true;
  }

  /**
   * 计算两向量余弦相似度。
   *
   * @param left  查询向量
   * @param right 库内向量
   * @return 相似度
   */
  private static double cosineSimilarity(float[] left, float[] right) {
    if (left.length != right.length) {
      return 0;
    }
    double dotProduct = 0;
    double normLeft = 0;
    double normRight = 0;
    for (int index = 0; index < left.length; index++) {
      dotProduct += left[index] * right[index];
      normLeft += left[index] * left[index];
      normRight += right[index] * right[index];
    }
    if (normLeft == 0 || normRight == 0) {
      return 0;
    }
    return dotProduct / (Math.sqrt(normLeft) * Math.sqrt(normRight));
  }
}
