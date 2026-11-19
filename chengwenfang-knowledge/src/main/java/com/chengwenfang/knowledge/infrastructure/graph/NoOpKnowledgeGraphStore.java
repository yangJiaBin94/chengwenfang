package com.chengwenfang.knowledge.infrastructure.graph;

import com.chengwenfang.knowledge.facade.dto.view.KnowledgeCitationView;
import com.chengwenfang.knowledge.facade.spi.KnowledgeGraphStore;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Graph 关闭时的空实现。
 */
@Component
@ConditionalOnProperty(
    prefix = "chengwenfang.knowledge.graph",
    name = "enabled",
    havingValue = "false",
    matchIfMissing = true)
public class NoOpKnowledgeGraphStore implements KnowledgeGraphStore {

  /** {@inheritDoc} */
  @Override
  public boolean enabled() {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean available() {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<KnowledgeCitationView> searchLocal(String query, int topK) {
    return List.of();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<KnowledgeCitationView> searchGlobal(String query, int topK) {
    return List.of();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteEdgesBySourceChunkId(String sourceChunkId) {
    // no-op
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteByDocumentId(String documentId, List<String> chunkIds) {
    // no-op
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void upsertChunkMentions(
      String chunkId,
      String documentTitle,
      String headingPath,
      String snippet,
      String indexVersion,
      double confidence) {
    // no-op
  }

  /** {@inheritDoc} */
  @Override
  public void setEdgeEnabled(String edgeUid, boolean enabled) {
    // no-op
  }

  /** {@inheritDoc} */
  @Override
  public void deleteByIndexVersion(String indexVersion) {
    // no-op
  }
}
