package com.chengwenfang.knowledge.facade.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** {@link KnowledgeChunkProfile} 解析。 */
class KnowledgeChunkProfileTest {

  @Test
  void fromParsesKnownValues() {
    assertThat(KnowledgeChunkProfile.from("markdown_structured"))
        .isEqualTo(KnowledgeChunkProfile.MARKDOWN_STRUCTURED);
    assertThat(KnowledgeChunkProfile.from("PLAIN")).isEqualTo(KnowledgeChunkProfile.PLAIN);
  }

  @Test
  void fromRejectsBlank() {
    assertThatThrownBy(() -> KnowledgeChunkProfile.from(" "))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
