package com.chengwenfang.mode.hotnews.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chengwenfang.ai.facade.AiVisionFacade;
import com.chengwenfang.ai.facade.dto.request.VisionCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.VisionCompleteResult;
import com.chengwenfang.ai.facade.dto.view.WebSearchCitation;
import com.chengwenfang.ai.facade.enums.WebSearchMode;
import com.chengwenfang.crawler.facade.dto.view.SearchHitView;
import com.chengwenfang.mode.hotnews.application.research.ResearchAiWebSearchCollectResult;
import com.chengwenfang.mode.hotnews.application.research.ResearchAiWebSearchItem;
import com.chengwenfang.mode.hotnews.application.research.ResearchSourcePolicy;
import com.chengwenfang.mode.hotnews.domain.TopicResearchPlanner;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** {@link ResearchAiWebSearchCollector} 能力门控与闸门。 */
class ResearchAiWebSearchCollectorTest {

  @Test
  void collect_skips_when_web_search_unsupported() {
    AiVisionFacade ai = mock(AiVisionFacade.class);
    when(ai.supportsWebSearch()).thenReturn(false);
    ResearchAiWebSearchCollector collector = new ResearchAiWebSearchCollector(ai);

    ResearchAiWebSearchCollectResult result = collector.collect(
        "苹果财报", "营收超预期", Set.of(), ResearchSourcePolicy.builder().build());

    assertThat(result.isUsed()).isFalse();
    assertThat(result.getSkipReason()).contains("不具备 WebSearch");
    assertThat(result.getHits()).isEmpty();
    verify(ai, never()).complete(any());
  }

  @Test
  void collect_skips_when_citations_empty() {
    AiVisionFacade ai = mock(AiVisionFacade.class);
    when(ai.supportsWebSearch()).thenReturn(true);
    when(ai.complete(any())).thenReturn(VisionCompleteResult.builder()
        .text("草稿")
        .citations(List.of())
        .build());
    ResearchAiWebSearchCollector collector = new ResearchAiWebSearchCollector(ai);

    ResearchAiWebSearchCollectResult result = collector.collect(
        "苹果财报", "", Set.of(), null);

    assertThat(result.isUsed()).isTrue();
    assertThat(result.getSkipReason()).contains("未返回引用");
    assertThat(result.getHits()).isEmpty();
    ArgumentCaptor<VisionCompleteRequest> captor = ArgumentCaptor.forClass(VisionCompleteRequest.class);
    verify(ai).complete(captor.capture());
    assertThat(captor.getValue().getWebSearchMode()).isEqualTo(WebSearchMode.ON);
  }

  @Test
  void collect_maps_gated_items_to_hits() {
    AiVisionFacade ai = mock(AiVisionFacade.class);
    when(ai.supportsWebSearch()).thenReturn(true);
    when(ai.complete(any())).thenAnswer(invocation -> {
      VisionCompleteRequest request = invocation.getArgument(0);
      if (request.getWebSearchMode() != null && request.getWebSearchMode().isEnabled()) {
        return VisionCompleteResult.builder()
            .text("联网草稿")
            .citations(List.of(
                WebSearchCitation.builder()
                    .url("https://news.example/apple")
                    .title("苹果财报解读")
                    .build(),
                WebSearchCitation.builder()
                    .url("https://news.example/other")
                    .title("另一篇")
                    .build()))
            .build();
      }
      return VisionCompleteResult.builder()
          .text("""
              {"items":[
                {"title":"财报解读","url":"https://news.example/apple","summary":"营收增长"},
                {"title":"幻觉链接","url":"https://fake.example/x","summary":"应丢弃"}
              ]}
              """)
          .build();
    });
    ResearchAiWebSearchCollector collector = new ResearchAiWebSearchCollector(ai);
    String occupied = TopicResearchPlanner.normalizeUrl("https://occupied.example/a");

    ResearchAiWebSearchCollectResult result = collector.collect(
        "苹果财报",
        "营收",
        Set.of(occupied),
        ResearchSourcePolicy.builder()
            .blockedDomains(List.of("blocked.example"))
            .build());

    assertThat(result.isUsed()).isTrue();
    assertThat(result.getCitationCount()).isEqualTo(2);
    assertThat(result.getHits()).hasSize(1);
    SearchHitView hit = result.getHits().get(0);
    assertThat(hit.getUrl()).isEqualTo("https://news.example/apple");
    assertThat(hit.getTitle()).isEqualTo("财报解读");
    assertThat(hit.getSnippet()).isEqualTo("营收增长");
  }

  @Test
  void toHits_filters_occupied_blocked_and_marketing() {
    ResearchAiWebSearchCollector collector = new ResearchAiWebSearchCollector(mock(AiVisionFacade.class));
    String occupiedKey = TopicResearchPlanner.normalizeUrl("https://news.example/old");
    List<SearchHitView> hits = collector.toHits(
        List.of(
            ResearchAiWebSearchItem.builder()
                .title("旧稿")
                .url("https://news.example/old")
                .summary("已占用")
                .build(),
            ResearchAiWebSearchItem.builder()
                .title("屏蔽站")
                .url("https://spam.blocked.example/a")
                .summary("应丢")
                .build(),
            ResearchAiWebSearchItem.builder()
                .title("点击购买优惠券")
                .url("https://news.example/promo")
                .summary("导购话术")
                .build(),
            ResearchAiWebSearchItem.builder()
                .title("有效")
                .url("https://news.example/ok")
                .summary("可用")
                .build()),
        Set.of(occupiedKey),
        ResearchSourcePolicy.builder()
            .blockedDomains(List.of("blocked.example"))
            .build());
    assertThat(hits).hasSize(1);
    assertThat(hits.get(0).getUrl()).isEqualTo("https://news.example/ok");
  }
}
