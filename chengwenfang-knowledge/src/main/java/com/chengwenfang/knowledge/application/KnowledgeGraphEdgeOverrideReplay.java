package com.chengwenfang.knowledge.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.knowledge.domain.entity.KnowledgeChunkEntity;
import com.chengwenfang.knowledge.domain.entity.KnowledgeGraphEdgeOverrideEntity;
import com.chengwenfang.knowledge.facade.constant.KnowledgeGraphConstants;
import com.chengwenfang.knowledge.facade.spi.KnowledgeGraphStore;
import com.chengwenfang.knowledge.infrastructure.mapper.KnowledgeChunkMapper;
import com.chengwenfang.knowledge.infrastructure.mapper.KnowledgeGraphEdgeOverrideMapper;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 图边旁路按 chunkIndex 回放到新切块（重建 / 蓝绿写版本后调用）。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeGraphEdgeOverrideReplay {

  /** 旁路表。 */
  private final KnowledgeGraphEdgeOverrideMapper overrideMapper;
  /** 切块。 */
  private final KnowledgeChunkMapper chunkMapper;
  /** 图存储。 */
  private final KnowledgeGraphStore graphStore;

  /**
   * 索引变更前快照：旧切块序号与旁路行。
   *
   * @param documentId 文档 ID
   * @return 快照；无切块时为空快照
   */
  public Snapshot capture(String documentId) {
    if (StrUtil.isBlank(documentId)) {
      return Snapshot.empty();
    }
    List<KnowledgeChunkEntity> existingChunks = chunkMapper.selectList(
        new LambdaQueryWrapper<KnowledgeChunkEntity>()
            .eq(KnowledgeChunkEntity::getDocumentId, documentId));
    Map<String, Integer> oldChunkIndexById = new HashMap<>();
    for (KnowledgeChunkEntity chunk : existingChunks) {
      if (chunk == null || StrUtil.isBlank(chunk.getId())) {
        continue;
      }
      oldChunkIndexById.put(chunk.getId(), ObjectUtil.defaultIfNull(chunk.getChunkIndex(), -1));
    }
    if (oldChunkIndexById.isEmpty()) {
      return Snapshot.empty();
    }
    List<KnowledgeGraphEdgeOverrideEntity> overrides = overrideMapper.selectList(
        new LambdaQueryWrapper<KnowledgeGraphEdgeOverrideEntity>()
            .in(KnowledgeGraphEdgeOverrideEntity::getSourceChunkId, oldChunkIndexById.keySet()));
    return new Snapshot(oldChunkIndexById, overrides);
  }

  /**
   * 将快照中的旁路映射到目标版本同 chunkIndex 切块（仅写 MySQL）。
   *
   * <p>图 enabled 须在边 upsert 之后调用 {@link #syncEnabledToGraph(String, String)}。
   *
   * <p>shortcut: 仅回放 {@code mentions:{chunkId}} 边；其它边类型需扩展 UID 推导。
   *
   * @param documentId    文档 ID
   * @param targetVersion 目标索引版本
   * @param snapshot      {@link #capture(String)} 结果
   */
  public void replay(String documentId, String targetVersion, Snapshot snapshot) {
    if (StrUtil.isBlank(documentId)
        || StrUtil.isBlank(targetVersion)
        || snapshot == null
        || CollUtil.isEmpty(snapshot.overrides())) {
      return;
    }
    List<KnowledgeChunkEntity> newChunks = chunkMapper.selectList(
        new LambdaQueryWrapper<KnowledgeChunkEntity>()
            .eq(KnowledgeChunkEntity::getDocumentId, documentId)
            .eq(KnowledgeChunkEntity::getIndexVersion, targetVersion));
    Map<Integer, String> newChunkIdByIndex = newChunks.stream()
        .filter(Objects::nonNull)
        .filter(chunk -> StrUtil.isNotBlank(chunk.getId()))
        .collect(Collectors.toMap(
            chunk -> ObjectUtil.defaultIfNull(chunk.getChunkIndex(), -1),
            KnowledgeChunkEntity::getId,
            (first, ignored) -> first));
    LocalDateTime now = LocalDateTime.now();
    for (KnowledgeGraphEdgeOverrideEntity override : snapshot.overrides()) {
      if (override == null || StrUtil.isBlank(override.getSourceChunkId())) {
        continue;
      }
      Integer chunkIndex = snapshot.oldChunkIndexById().get(override.getSourceChunkId());
      if (chunkIndex == null || chunkIndex < 0) {
        continue;
      }
      String newChunkId = newChunkIdByIndex.get(chunkIndex);
      if (StrUtil.isBlank(newChunkId)) {
        continue;
      }
      String newEdgeUid = KnowledgeGraphConstants.mentionsEdgeUid(newChunkId);
      override.setSourceChunkId(newChunkId);
      override.setNeo4jEdgeUid(newEdgeUid);
      override.setUpdatedAt(now);
      overrideMapper.updateById(override);
    }
  }

  /**
   * 将目标版本旁路的 enabled 同步到图（须在边已 upsert 之后调用）。
   *
   * @param documentId    文档 ID
   * @param targetVersion 目标索引版本
   */
  public void syncEnabledToGraph(String documentId, String targetVersion) {
    if (StrUtil.isBlank(documentId) || StrUtil.isBlank(targetVersion) || !graphStore.available()) {
      return;
    }
    List<KnowledgeChunkEntity> chunks = chunkMapper.selectList(
        new LambdaQueryWrapper<KnowledgeChunkEntity>()
            .eq(KnowledgeChunkEntity::getDocumentId, documentId)
            .eq(KnowledgeChunkEntity::getIndexVersion, targetVersion));
    List<String> chunkIds = chunks.stream()
        .filter(Objects::nonNull)
        .map(KnowledgeChunkEntity::getId)
        .filter(StrUtil::isNotBlank)
        .toList();
    if (CollUtil.isEmpty(chunkIds)) {
      return;
    }
    List<KnowledgeGraphEdgeOverrideEntity> overrides = overrideMapper.selectList(
        new LambdaQueryWrapper<KnowledgeGraphEdgeOverrideEntity>()
            .in(KnowledgeGraphEdgeOverrideEntity::getSourceChunkId, chunkIds));
    for (KnowledgeGraphEdgeOverrideEntity override : overrides) {
      if (override == null || StrUtil.isBlank(override.getNeo4jEdgeUid())) {
        continue;
      }
      graphStore.setEdgeEnabled(
          override.getNeo4jEdgeUid(), Boolean.TRUE.equals(override.getEnabled()));
    }
  }

  /**
   * 旁路回放快照。
   *
   * @param oldChunkIndexById 旧切块 ID → chunkIndex
   * @param overrides         旁路行
   */
  public record Snapshot(
      Map<String, Integer> oldChunkIndexById,
      List<KnowledgeGraphEdgeOverrideEntity> overrides) {

    /**
     * 空快照。
     *
     * @return 空
     */
    public static Snapshot empty() {
      return new Snapshot(Map.of(), List.of());
    }
  }
}
