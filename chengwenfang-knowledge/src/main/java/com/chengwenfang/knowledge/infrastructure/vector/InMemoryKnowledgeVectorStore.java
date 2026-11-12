package com.chengwenfang.knowledge.infrastructure.vector;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存向量存储：用于 Qdrant 未启用或集成测试；基于余弦相似度检索。
 */
public class InMemoryKnowledgeVectorStore implements KnowledgeVectorStore {

  /** 点 ID → 向量点。 */
  private final ConcurrentHashMap<String, KnowledgeVectorPoint> points = new ConcurrentHashMap<>();

  /**
   * {@inheritDoc}
   */
  @Override
  public void upsert(List<KnowledgeVectorPoint> pointList) {
    if (CollUtil.isEmpty(pointList)) {
      return;
    }
    for (KnowledgeVectorPoint point : pointList) {
      if (point == null || StrUtil.isBlank(point.getId())) {
        continue;
      }
      points.put(point.getId(), point);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteByDocumentId(String documentId) {
    if (StrUtil.isBlank(documentId)) {
      return;
    }
    points.entrySet().removeIf(entry -> documentId.equals(entry.getValue().getDocumentId()));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<KnowledgeVectorMatch> search(float[] query, int topK, KnowledgeVectorFilter filter) {
    if (query == null || query.length == 0 || topK <= 0) {
      return List.of();
    }
    List<KnowledgeVectorMatch> matches = new ArrayList<>();
    for (KnowledgeVectorPoint point : points.values()) {
      if (!matchesFilter(point, filter)) {
        continue;
      }
      if (point.getEmbedding() == null || point.getEmbedding().length == 0) {
        continue;
      }
      double score = cosineSimilarity(query, point.getEmbedding());
      matches.add(toMatch(point, score));
    }
    matches.sort(Comparator.comparingDouble(KnowledgeVectorMatch::getScore).reversed());
    if (matches.size() > topK) {
      return new ArrayList<>(matches.subList(0, topK));
    }
    return matches;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void ensureCollection(int dimension) {
    // 内存实现无需预创建集合
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean healthy() {
    return true;
  }

  /**
   * 判断点是否满足检索过滤条件。
   *
   * @param point  向量点
   * @param filter 过滤条件
   * @return 是否命中过滤
   */
  private boolean matchesFilter(KnowledgeVectorPoint point, KnowledgeVectorFilter filter) {
    if (filter == null) {
      return true;
    }
    if (StrUtil.isNotBlank(filter.getDocumentId())
        && !filter.getDocumentId().equals(point.getDocumentId())) {
      return false;
    }
    if (StrUtil.isNotBlank(filter.getSourceKind())
        && !filter.getSourceKind().equals(point.getSourceKind())) {
      return false;
    }
    if (StrUtil.isNotBlank(filter.getMediaKind())
        && !filter.getMediaKind().equals(point.getMediaKind())) {
      return false;
    }
    if (StrUtil.isNotBlank(filter.getThemeId()) && !filter.getThemeId().equals(point.getThemeId())) {
      return false;
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

  /**
   * 将存储点转为检索命中视图。
   *
   * @param point 向量点
   * @param score 相似度
   * @return 命中结果
   */
  private static KnowledgeVectorMatch toMatch(KnowledgeVectorPoint point, double score) {
    return KnowledgeVectorMatch.builder()
        .score(score)
        .id(point.getId())
        .documentId(point.getDocumentId())
        .sourceKind(point.getSourceKind())
        .sourceRefId(point.getSourceRefId())
        .mediaKind(point.getMediaKind())
        .title(point.getTitle())
        .headingPath(point.getHeadingPath())
        .chunkIndex(point.getChunkIndex())
        .contentHash(point.getContentHash())
        .themeId(point.getThemeId())
        .deepLink(point.getDeepLink())
        .text(point.getText())
        .build();
  }
}
