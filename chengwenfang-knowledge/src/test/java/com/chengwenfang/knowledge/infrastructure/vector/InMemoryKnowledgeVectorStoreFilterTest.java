package com.chengwenfang.knowledge.infrastructure.vector;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.knowledge.facade.spi.KnowledgeVectorFilter;
import com.chengwenfang.knowledge.facade.spi.KnowledgeVectorHit;
import com.chengwenfang.knowledge.facade.spi.KnowledgeVectorUpsert;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 内存向量库多选来源/介质过滤。 */
class InMemoryKnowledgeVectorStoreFilterTest {

  @Test
  void filtersByMultipleSourceKinds() {
    InMemoryKnowledgeVectorStore store = new InMemoryKnowledgeVectorStore();
    store.upsert(List.of(
        point("c1", "work_output", "article_markdown"),
        point("c2", "channel_item", "article_markdown"),
        point("c3", "theme_note", "plain_text")));
    float[] query = new float[] {1f, 0f};
    List<KnowledgeVectorHit> hits = store.search(
        query,
        10,
        KnowledgeVectorFilter.builder()
            .sourceKinds(List.of("work_output", "channel_item"))
            .build());
    assertThat(hits).extracting(KnowledgeVectorHit::getChunkId).containsExactlyInAnyOrder("c1", "c2");
  }

  @Test
  void filtersByMultipleMediaKinds() {
    InMemoryKnowledgeVectorStore store = new InMemoryKnowledgeVectorStore();
    store.upsert(List.of(
        point("c1", "work_output", "article_markdown"),
        point("c2", "work_output", "plain_text"),
        point("c3", "work_output", "transcript")));
    float[] query = new float[] {1f, 0f};
    List<KnowledgeVectorHit> hits = store.search(
        query,
        10,
        KnowledgeVectorFilter.builder()
            .mediaKinds(List.of("article_markdown", "transcript"))
            .build());
    assertThat(hits).extracting(KnowledgeVectorHit::getChunkId).containsExactlyInAnyOrder("c1", "c3");
  }

  /**
   * 构造同向单位向量点，便于过滤断言。
   *
   * @param chunkId   切块 ID
   * @param sourceKind 来源
   * @param mediaKind  介质
   * @return upsert
   */
  private static KnowledgeVectorUpsert point(String chunkId, String sourceKind, String mediaKind) {
    return KnowledgeVectorUpsert.builder()
        .chunkId(chunkId)
        .documentId("doc-" + chunkId)
        .sourceKind(sourceKind)
        .mediaKind(mediaKind)
        .indexVersion("v1")
        .vector(new float[] {1f, 0f})
        .content("x")
        .build();
  }
}
