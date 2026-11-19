package com.chengwenfang.knowledge.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.job.facade.JobFacade;
import com.chengwenfang.job.facade.constant.JobTypes;
import com.chengwenfang.job.facade.dto.job.KnowledgeIndexDocumentJobInput;
import com.chengwenfang.knowledge.domain.entity.KnowledgeChunkEntity;
import com.chengwenfang.knowledge.domain.entity.KnowledgeDocumentEntity;
import com.chengwenfang.knowledge.facade.constant.KnowledgeJobEntityTypes;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeReconcileResultView;
import com.chengwenfang.knowledge.facade.enums.KnowledgeDocumentStatus;
import com.chengwenfang.knowledge.facade.spi.KnowledgeGraphStore;
import com.chengwenfang.knowledge.facade.spi.KnowledgeVectorStore;
import com.chengwenfang.knowledge.infrastructure.mapper.KnowledgeChunkMapper;
import com.chengwenfang.knowledge.infrastructure.mapper.KnowledgeDocumentMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * MySQL / 向量 / 图三存储对账：清孤儿切块与图边，缺索引文档入队重建。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeReconcileService {

  /** 文档 Mapper。 */
  private final KnowledgeDocumentMapper documentMapper;
  /** 切块 Mapper。 */
  private final KnowledgeChunkMapper chunkMapper;
  /** 向量库。 */
  private final KnowledgeVectorStore vectorStore;
  /** 图库。 */
  private final KnowledgeGraphStore graphStore;
  /** 任务门面。 */
  private final JobFacade jobFacade;

  /**
   * 执行一轮对账（默认落库）。
   *
   * @return 结果摘要
   */
  public KnowledgeReconcileResultView reconcile() {
    return reconcile(false);
  }

  /**
   * 执行一轮对账。
   *
   * @param dryRun true 时只统计预览，不删库、不入队
   * @return 结果摘要
   */
  public KnowledgeReconcileResultView reconcile(boolean dryRun) {
    int orphanChunksRemoved = 0;
    int graphsCleared = 0;
    int reindexEnqueued = 0;

    // 孤儿切块：所属文档已不存在（SQL 侧 JOIN，避免全表加载进内存）
    List<KnowledgeChunkEntity> orphanChunks = chunkMapper.selectOrphanChunks();
    if (CollUtil.isNotEmpty(orphanChunks)) {
      Set<String> orphanDocIds = orphanChunks.stream()
          .map(KnowledgeChunkEntity::getDocumentId)
          .filter(StrUtil::isNotBlank)
          .collect(Collectors.toSet());
      for (String orphanDocId : orphanDocIds) {
        List<String> chunkIds = orphanChunks.stream()
            .filter(chunk -> orphanDocId.equals(chunk.getDocumentId()))
            .map(KnowledgeChunkEntity::getId)
            .toList();
        if (!dryRun) {
          vectorStore.deleteByDocumentId(orphanDocId);
          graphStore.deleteByDocumentId(orphanDocId, chunkIds);
        }
        graphsCleared++;
      }
      for (KnowledgeChunkEntity orphan : orphanChunks) {
        if (!dryRun) {
          chunkMapper.deleteById(orphan.getId());
        }
        orphanChunksRemoved++;
      }
    }

    // READY 但无切块 → 入队重建（不在此同步狂打 embed）
    List<KnowledgeDocumentEntity> readyDocs = documentMapper.selectList(
        new LambdaQueryWrapper<KnowledgeDocumentEntity>()
            .eq(KnowledgeDocumentEntity::getStatus, KnowledgeDocumentStatus.READY.getValue()));
    List<String> enqueued = new ArrayList<>();
    for (KnowledgeDocumentEntity document : readyDocs) {
      Long count = chunkMapper.selectCount(
          new LambdaQueryWrapper<KnowledgeChunkEntity>()
              .eq(KnowledgeChunkEntity::getDocumentId, document.getId()));
      if (count != null && count > 0) {
        continue;
      }
      if (!dryRun) {
        jobFacade.create(
            JobTypes.KNOWLEDGE_INDEX_DOCUMENT,
            KnowledgeJobEntityTypes.DOCUMENT,
            document.getId(),
            KnowledgeIndexDocumentJobInput.builder().documentId(document.getId()).build());
      }
      enqueued.add(document.getId());
      reindexEnqueued++;
    }

    log.info(
        "知识库对账完成 dryRun={} orphanChunks={} graphsCleared={} reindexEnqueued={}",
        dryRun,
        orphanChunksRemoved,
        graphsCleared,
        reindexEnqueued);
    return KnowledgeReconcileResultView.builder()
        .dryRun(dryRun)
        .orphanChunksRemoved(orphanChunksRemoved)
        .graphDocumentsCleared(graphsCleared)
        .reindexEnqueued(reindexEnqueued)
        .enqueuedDocumentIds(enqueued)
        .build();
  }
}
