package com.chengwenfang.knowledge.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.knowledge.domain.entity.KnowledgeChunkEntity;
import com.chengwenfang.knowledge.domain.entity.KnowledgeGraphEdgeOverrideEntity;
import com.chengwenfang.knowledge.facade.constant.KnowledgeGraphConstants;
import com.chengwenfang.knowledge.facade.spi.KnowledgeGraphStore;
import com.chengwenfang.knowledge.infrastructure.mapper.KnowledgeChunkMapper;
import com.chengwenfang.knowledge.infrastructure.mapper.KnowledgeGraphEdgeOverrideMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 图边纠错：低置信默认禁用、人工启停、按 sourceChunkId 增量重索引。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeGraphEdgeCorrectionService {

  /** 旁路表。 */
  private final KnowledgeGraphEdgeOverrideMapper overrideMapper;
  /** 图存储。 */
  private final KnowledgeGraphStore graphStore;
  /** 切块。 */
  private final KnowledgeChunkMapper chunkMapper;
  /** 索引流水线（重建提及边；内含旁路回放）。 */
  private final KnowledgeIndexPipeline indexPipeline;

  /**
   * 登记或更新边覆盖，并同步 Neo4j {@code r.enabled}。
   *
   * @param edgeUid       边 UID
   * @param sourceChunkId 来源块
   * @param confidence    置信度
   * @param note          说明
   * @param enabled       显式启停；null 时按置信度
   */
  @Transactional
  public void upsertOverride(
      String edgeUid,
      String sourceChunkId,
      Double confidence,
      String note,
      Boolean enabled) {
    if (StrUtil.isBlank(edgeUid)) {
      return;
    }
    boolean enable = enabled != null
        ? enabled
        : confidence == null || confidence >= KnowledgeGraphConstants.LOW_CONFIDENCE;
    KnowledgeGraphEdgeOverrideEntity existing = overrideMapper.selectOne(
        new LambdaQueryWrapper<KnowledgeGraphEdgeOverrideEntity>()
            .eq(KnowledgeGraphEdgeOverrideEntity::getNeo4jEdgeUid, edgeUid)
            .last("LIMIT 1"));
    LocalDateTime now = LocalDateTime.now();
    if (existing == null) {
      overrideMapper.insert(KnowledgeGraphEdgeOverrideEntity.builder()
          .id(IdsUtil.newId())
          .neo4jEdgeUid(edgeUid)
          .sourceChunkId(sourceChunkId)
          .enabled(enable)
          .confidence(confidence)
          .note(note)
          .updatedAt(now)
          .build());
    } else {
      existing.setSourceChunkId(sourceChunkId);
      existing.setEnabled(enable);
      existing.setConfidence(confidence);
      existing.setNote(note);
      existing.setUpdatedAt(now);
      overrideMapper.updateById(existing);
    }
    // 旁路表与图检索属性同步，避免只改 MySQL 无效
    graphStore.setEdgeEnabled(edgeUid, enable);
  }

  /**
   * 重跑所属文档索引以重抽提及边；旁路由流水线按 chunkIndex 回放，不预删图边。
   *
   * @param sourceChunkId 切块 ID
   */
  @Transactional
  public void rebuildEdgesForChunk(String sourceChunkId) {
    if (StrUtil.isBlank(sourceChunkId)) {
      return;
    }
    KnowledgeChunkEntity seedChunk = chunkMapper.selectById(sourceChunkId);
    if (seedChunk == null || StrUtil.isBlank(seedChunk.getDocumentId())) {
      return;
    }
    indexPipeline.indexDocument(seedChunk.getDocumentId());
  }
}
