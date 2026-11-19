package com.chengwenfang.knowledge.facade.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 检索模式枚举单测。 */
class KnowledgeRetrieveModeTest {

  @Test
  void fromBlankDefaultsHybrid() {
    assertThat(KnowledgeRetrieveMode.from(null)).isEqualTo(KnowledgeRetrieveMode.HYBRID);
    assertThat(KnowledgeRetrieveMode.from("")).isEqualTo(KnowledgeRetrieveMode.HYBRID);
  }

  @Test
  void fromKnownValues() {
    assertThat(KnowledgeRetrieveMode.from("graph_local")).isEqualTo(KnowledgeRetrieveMode.GRAPH_LOCAL);
    assertThat(KnowledgeRetrieveMode.from("ABSTAIN")).isEqualTo(KnowledgeRetrieveMode.ABSTAIN);
  }
}
