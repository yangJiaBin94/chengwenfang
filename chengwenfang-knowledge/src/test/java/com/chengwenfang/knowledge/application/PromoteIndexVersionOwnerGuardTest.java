package com.chengwenfang.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.knowledge.facade.spi.KnowledgeVectorHit;
import com.chengwenfang.knowledge.facade.spi.KnowledgeVectorUpsert;
import com.chengwenfang.knowledge.infrastructure.vector.InMemoryKnowledgeVectorStore;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 同名版本字符串下，按 owner GC 不得误删其他用户向量点。
 */
class PromoteIndexVersionOwnerGuardTest {

  @Test
  void deleteByIndexVersionAndOwnerDoesNotTouchOtherOwners() {
    InMemoryKnowledgeVectorStore store = new InMemoryKnowledgeVectorStore();
    store.ensureCollection(2);
    store.upsert(List.of(
        KnowledgeVectorUpsert.builder()
            .chunkId("a1")
            .documentId("doc-a")
            .ownerUserId("owner-a")
            .indexVersion("v1")
            .vector(new float[] {1f, 0f})
            .build(),
        KnowledgeVectorUpsert.builder()
            .chunkId("b1")
            .documentId("doc-b")
            .ownerUserId("owner-b")
            .indexVersion("v1")
            .vector(new float[] {0f, 1f})
            .build()));

    store.deleteByIndexVersionAndOwner("v1", "owner-a");

    List<KnowledgeVectorHit> hits = store.search(new float[] {0f, 1f}, 10);
    assertThat(hits).extracting(KnowledgeVectorHit::getChunkId).containsExactly("b1");
  }
}
