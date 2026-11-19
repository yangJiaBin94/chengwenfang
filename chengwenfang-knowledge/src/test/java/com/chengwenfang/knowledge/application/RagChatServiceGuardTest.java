package com.chengwenfang.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.knowledge.facade.constant.KnowledgeRagConstants;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeCitationView;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 寒暄短路、拒答契约与引用去重。 */
class RagChatServiceGuardTest {

  @Test
  void abstainMessageMatchesDesignContract() {
    assertThat(KnowledgeRagConstants.ABSTAIN_MESSAGE).contains("无法基于现有资料回答");
    assertThat(KnowledgeRagConstants.ABSTAIN_MESSAGE).contains("已入库作品");
    assertThat(KnowledgeRagConstants.MIN_CITATIONS_TO_ANSWER).isGreaterThanOrEqualTo(1);
  }


  @Test
  void chitchatDetectsGreeting() {
    assertThat(RagChatService.isChitchat("你好")).isTrue();
    assertThat(RagChatService.isChitchat("你好！")).isTrue();
    assertThat(RagChatService.isChitchat("AI 的定义是什么")).isFalse();
  }

  @Test
  void dedupeKeepsFirstBySnippetEvenIfHeadingDiffers() {
    KnowledgeCitationView first = KnowledgeCitationView.builder()
        .documentId("d1")
        .chunkId("c1")
        .headingPath("根标题")
        .snippet("人工智能不等于机器人。")
        .build();
    KnowledgeCitationView duplicate = KnowledgeCitationView.builder()
        .documentId("d1")
        .chunkId("c2")
        .headingPath("开场与本课目标")
        .snippet("人工智能不等于机器人。")
        .build();
    List<KnowledgeCitationView> result =
        RagChatService.dedupeCitations(List.of(first, duplicate));
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getChunkId()).isEqualTo("c1");
  }

  @Test
  void extractRememberContentFromExplicitAsk() {
    assertThat(RagChatService.extractRememberContent("请记住：我偏好短句回答"))
        .isEqualTo("我偏好短句回答");
    assertThat(RagChatService.extractRememberContent("AI 是什么")).isBlank();
    assertThat(RagChatService.extractRememberContent("如何记住重点")).isBlank();
  }

  @Test
  void keepSummaryTailKeepsRecentTurns() {
    String summary = "AAAA" + "BBBB";
    assertThat(RagChatService.keepSummaryTail(summary, 4)).isEqualTo("BBBB");
  }

  @Test
  void dropsIllustrationPlaceholderCitations() {
    KnowledgeCitationView prose = KnowledgeCitationView.builder()
        .chunkId("c1")
        .snippet("很多人以为 AI 就是机器人，其实不然。")
        .build();
    KnowledgeCitationView art = KnowledgeCitationView.builder()
        .chunkId("c2")
        .snippet("插图位置\n课程封面\n核心内容：人工智能≠机器人")
        .build();
    List<KnowledgeCitationView> result =
        RagChatService.dropLowValueCitations(List.of(prose, art));
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getChunkId()).isEqualTo("c1");
  }
}
