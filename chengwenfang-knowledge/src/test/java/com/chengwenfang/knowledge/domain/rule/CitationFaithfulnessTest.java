package com.chengwenfang.knowledge.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.knowledge.domain.model.EvidenceSnippet;
import com.chengwenfang.knowledge.facade.constant.KnowledgeRagConstants;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 忠实度粗检单测。 */
class CitationFaithfulnessTest {

  @Test
  void abstainMessagePasses() {
    assertThat(CitationFaithfulness.hasGroundedSpan(
            KnowledgeRagConstants.ABSTAIN_MESSAGE + "。", List.of()))
        .isTrue();
  }

  @Test
  void snippetOverlapPasses() {
    EvidenceSnippet evidence = EvidenceSnippet.builder()
        .snippet("引流方法包括私域裂变与内容矩阵")
        .build();
    assertThat(CitationFaithfulness.hasGroundedSpan(
            "文中提到引流方法包括私域裂变", List.of(evidence)))
        .isTrue();
  }

  @Test
  void emptyCitationsFailForNormalAnswer() {
    assertThat(CitationFaithfulness.hasGroundedSpan("随便编一个结论", List.of())).isFalse();
  }

  @Test
  void citationMarkerPasses() {
    EvidenceSnippet evidence = EvidenceSnippet.builder()
        .snippet("完全不同的摘录内容不会出现在答案里")
        .title("某文档")
        .build();
    assertThat(CitationFaithfulness.hasGroundedSpan(
            "根据资料 [1] 可以确认该方法有效", List.of(evidence)))
        .isTrue();
  }
}
