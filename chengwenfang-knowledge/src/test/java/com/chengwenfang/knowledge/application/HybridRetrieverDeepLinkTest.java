package com.chengwenfang.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.knowledge.domain.entity.KnowledgeChunkEntity;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 引用深链锚定参数与进窗父块选择。 */
class HybridRetrieverDeepLinkTest {

  @Test
  void appendsHeadingAndSpan() {
    String link = HybridRetriever.enrichDeepLink(
        "/courses/course-1",
        "AI 的定义与边界 > 小结",
        120);
    assertThat(link).contains("citeHeading=");
    assertThat(link).contains("citeStart=120");
  }

  @Test
  void resolveWindowChunk_keepsHitWhenParentHeadingDiffers() {
    KnowledgeChunkEntity parent = chunk("p1", "封面", "插图位置：课程封面");
    KnowledgeChunkEntity hit = chunk("c1", "开场与本课目标", "1956 年的达特茅斯会议首次提出人工智能。");
    hit.setParentChunkId("p1");
    KnowledgeChunkEntity window = HybridRetriever.resolveWindowChunk(hit, Map.of("p1", parent, "c1", hit));
    assertThat(window.getId()).isEqualTo("c1");
  }

  @Test
  void resolveWindowChunk_usesSamePathParent() {
    KnowledgeChunkEntity parent = chunk("p1", "核心原理", "本节先讲路线交替，再讲爆发条件。");
    KnowledgeChunkEntity hit = chunk("c1", "核心原理", "爆发条件包括数据、算力与结构。");
    hit.setParentChunkId("p1");
    KnowledgeChunkEntity window = HybridRetriever.resolveWindowChunk(hit, Map.of("p1", parent, "c1", hit));
    assertThat(window.getId()).isEqualTo("p1");
  }

  private static KnowledgeChunkEntity chunk(String id, String headingPath, String content) {
    KnowledgeChunkEntity entity = new KnowledgeChunkEntity();
    entity.setId(id);
    entity.setHeadingPath(headingPath);
    entity.setContent(content);
    return entity;
  }
}
