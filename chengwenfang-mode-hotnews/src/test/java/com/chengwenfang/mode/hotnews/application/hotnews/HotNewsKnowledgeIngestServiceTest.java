package com.chengwenfang.mode.hotnews.application.hotnews;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.mode.hotnews.domain.entity.TopicResearchItemEntity;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsTopicSourceView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsTopicView;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link HotNewsKnowledgeIngestService} 纯函数自检。 */
class HotNewsKnowledgeIngestServiceTest {

  @Test
  void selectTop10_prefersRadarRankWithinLimit() {
    List<HotNewsTopicView> topics = List.of(
        topic("a", 3, 10),
        topic("b", 1, 90),
        topic("c", 11, 99),
        topic("d", 2, 80));
    List<HotNewsTopicView> selected = HotNewsKnowledgeIngestService.selectTop10(topics);
    assertThat(selected).extracting(HotNewsTopicView::getId).containsExactly("b", "d", "a");
  }

  @Test
  void buildTopicRawText_includesTitleSummaryAndSources() {
    HotNewsTopicView topic = HotNewsTopicView.builder()
        .id("t1")
        .title("选题标题")
        .summary("选题摘要")
        .build();
    List<HotNewsTopicSourceView> sources = List.of(
        HotNewsTopicSourceView.builder()
            .title("来源A")
            .url("https://example.com/a")
            .summary("事实速览A")
            .build());
    String text = HotNewsKnowledgeIngestService.buildTopicRawText(topic, sources);
    assertThat(text).contains("# 选题标题", "选题摘要", "来源A", "https://example.com/a", "事实速览A");
  }

  @Test
  void buildResearchRawText_includesTitleUrlSummary() {
    TopicResearchItemEntity item = TopicResearchItemEntity.builder()
        .id("r1")
        .title("资料标题")
        .url("https://example.com/r")
        .summary("资料摘要足够长")
        .build();
    String text = HotNewsKnowledgeIngestService.buildResearchRawText(item);
    assertThat(text).contains("# 资料标题", "https://example.com/r", "资料摘要足够长");
  }

  private static HotNewsTopicView topic(String id, int rank, int writingScore) {
    return HotNewsTopicView.builder()
        .id(id)
        .radarRank(rank)
        .writingScore(writingScore)
        .title(id)
        .build();
  }
}
