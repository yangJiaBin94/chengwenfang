package com.chengwenfang.knowledge.infrastructure.vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chengwenfang.infra.config.ChengwenfangProperties;
import com.chengwenfang.knowledge.facade.spi.KnowledgeVectorStore;
import org.junit.jupiter.api.Test;

/**
 * Qdrant 启用但连不上时不得回退内存向量库。
 */
class KnowledgeVectorConfigTest {

  @Test
  void disabledQdrantUsesMemoryAndIsDegraded() {
    ChengwenfangProperties.QdrantSettings settings = new ChengwenfangProperties.QdrantSettings();
    settings.setEnabled(false);

    KnowledgeVectorStore store = KnowledgeVectorConfig.open(settings);

    assertThat(store).isInstanceOf(InMemoryKnowledgeVectorStore.class);
    assertThat(store.degraded()).isTrue();
  }

  @Test
  void enabledButUnreachableQdrantDoesNotFallBackToMemory() {
    ChengwenfangProperties.QdrantSettings settings = new ChengwenfangProperties.QdrantSettings();
    settings.setEnabled(true);
    settings.setHost("127.0.0.1");
    settings.setGrpcPort(1);

    assertThatThrownBy(() -> KnowledgeVectorConfig.open(settings))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("拒绝回退内存向量库");
  }
}