package com.chengwenfang.knowledge.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.knowledge.domain.entity.KnowledgeDocumentEntity;
import com.chengwenfang.knowledge.facade.constant.KnowledgeChunkStrategyVersions;
import com.chengwenfang.knowledge.facade.enums.KnowledgeDocumentStatus;
import org.junit.jupiter.api.Test;

/** {@link KnowledgeIndexSkipPolicy} 策略版本与 hash。 */
class KnowledgeIndexSkipPolicyTest {

  @Test
  void skipsOnlyWhenHashStrategyAndChunkCountMatch() {
    KnowledgeDocumentEntity ready = KnowledgeDocumentEntity.builder()
        .status(KnowledgeDocumentStatus.READY.getValue())
        .chunkCount(3)
        .contentHash("abc")
        .chunkStrategyVersion(KnowledgeChunkStrategyVersions.CURRENT)
        .build();
    assertThat(KnowledgeIndexSkipPolicy.shouldSkip(ready, "abc", 3)).isTrue();
  }

  @Test
  void mismatchedPersistedChunkCountForcesReindex() {
    KnowledgeDocumentEntity ready = KnowledgeDocumentEntity.builder()
        .status(KnowledgeDocumentStatus.READY.getValue())
        .chunkCount(3)
        .contentHash("abc")
        .chunkStrategyVersion(KnowledgeChunkStrategyVersions.CURRENT)
        .build();
    assertThat(KnowledgeIndexSkipPolicy.shouldSkip(ready, "abc", 1)).isFalse();
  }

  @Test
  void nullStrategyVersionForcesReindex() {
    KnowledgeDocumentEntity ready = KnowledgeDocumentEntity.builder()
        .status(KnowledgeDocumentStatus.READY.getValue())
        .chunkCount(3)
        .contentHash("abc")
        .chunkStrategyVersion(null)
        .build();
    assertThat(KnowledgeIndexSkipPolicy.shouldSkip(ready, "abc", 3)).isFalse();
  }

  @Test
  void staleStrategyVersionForcesReindex() {
    KnowledgeDocumentEntity ready = KnowledgeDocumentEntity.builder()
        .status(KnowledgeDocumentStatus.READY.getValue())
        .chunkCount(3)
        .contentHash("abc")
        .chunkStrategyVersion("c1")
        .build();
    assertThat(KnowledgeIndexSkipPolicy.shouldSkip(ready, "abc", 3)).isFalse();
  }
}
