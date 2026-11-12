package com.chengwenfang.knowledge.infrastructure.vector;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.knowledge.domain.model.KnowledgeVectorHit;
import com.chengwenfang.knowledge.domain.model.KnowledgeVectorUpsert;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 将基础设施向量库适配为 facade SPI，供索引与检索注入。
 */
@Primary
@Component
@RequiredArgsConstructor
public class SpiKnowledgeVectorStoreBridge
    implements com.chengwenfang.knowledge.facade.spi.KnowledgeVectorStore {

  /** 实际向量存储（Qdrant 或内存）。 */
  private final KnowledgeVectorStore delegate;

  /**
   * {@inheritDoc}
   */
  @Override
  public void ensureCollection(int dimension) {
    delegate.ensureCollection(dimension);
  }

  /**
   * {@inheritDoc}
   *
   * @param items 向量记录
   */
  @Override
  public void upsert(List<KnowledgeVectorUpsert> items) {
    if (CollUtil.isEmpty(items)) {
      return;
    }
    List<KnowledgeVectorPoint> points = new ArrayList<>();
    for (KnowledgeVectorUpsert item : items) {
      if (item == null || StrUtil.isBlank(item.getChunkId()) || item.getVector() == null) {
        continue;
      }
      points.add(KnowledgeVectorPoint.builder()
          .id(item.getChunkId())
          .documentId(item.getDocumentId())
          .embedding(item.getVector())
          .build());
    }
    delegate.upsert(points);
  }

  /**
   * {@inheritDoc}
   *
   * @param queryVector 查询向量
   * @param limit       返回条数上限
   * @return 命中列表
   */
  @Override
  public List<KnowledgeVectorHit> search(float[] queryVector, int limit) {
    List<KnowledgeVectorMatch> matches =
        delegate.search(queryVector, limit, KnowledgeVectorFilter.builder().build());
    if (CollUtil.isEmpty(matches)) {
      return List.of();
    }
    List<KnowledgeVectorHit> hits = new ArrayList<>();
    for (KnowledgeVectorMatch match : matches) {
      if (match == null) {
        continue;
      }
      hits.add(KnowledgeVectorHit.builder()
          .chunkId(match.getId())
          .documentId(match.getDocumentId())
          .score(match.getScore())
          .build());
    }
    return hits;
  }

  /**
   * {@inheritDoc}
   *
   * @param documentId 文档 ID
   */
  @Override
  public void deleteByDocumentId(String documentId) {
    delegate.deleteByDocumentId(documentId);
  }
}
