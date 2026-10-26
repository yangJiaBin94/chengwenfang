package com.workbench.mode.hotnews.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.workbench.ai.facade.AiTextFacade;
import com.workbench.ai.facade.dto.request.TextCompleteRequest;
import com.workbench.ai.facade.dto.view.TextCompleteResult;
import com.workbench.mode.hotnews.application.model.ResearchCandidate;
import com.workbench.mode.hotnews.facade.enums.ResearchItemKind;
import com.workbench.mode.hotnews.infrastructure.mapper.TopicClusterMapper;
import com.workbench.mode.hotnews.infrastructure.mapper.TopicResearchItemMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** 资料池五维 AI 打分：成功解析与失败回退。 */
class TopicResearchItemScoreServiceTest {

  private AiTextFacade ai;
  private TopicResearchItemScoreService service;

  @BeforeEach
  void setUp() {
    ai = Mockito.mock(AiTextFacade.class);
    service = new TopicResearchItemScoreService(
        ai,
        Mockito.mock(TopicClusterMapper.class),
        Mockito.mock(TopicResearchItemMapper.class),
        Mockito.mock(ResearchItemScoreDetailsStore.class));
  }

  @Test
  void applyFallbackScore_按kind默认分() {
    ResearchCandidate web = ResearchCandidate.builder().kind(ResearchItemKind.WEB).build();
    ResearchCandidate source = ResearchCandidate.builder().kind(ResearchItemKind.SOURCE).build();
    ResearchCandidate manual = ResearchCandidate.builder().kind(ResearchItemKind.MANUAL).build();
    TopicResearchItemScoreService.applyFallbackScore(web);
    TopicResearchItemScoreService.applyFallbackScore(source);
    TopicResearchItemScoreService.applyFallbackScore(manual);
    assertThat(web.getScore()).isEqualTo(80);
    assertThat(source.getScore()).isEqualTo(70);
    assertThat(manual.getScore()).isEqualTo(85);
  }

  @Test
  void scoreCandidates_AI成功时写回加权综合分与分维() {
    when(ai.completeText(any(TextCompleteRequest.class))).thenReturn(
        TextCompleteResult.builder()
            .text("""
                {"items":[{"id":"0","dimensions":[
                  {"key":"relevance","score":10,"reason":"紧扣选题"},
                  {"key":"credibility","score":8,"reason":"权威来源"},
                  {"key":"freshness","score":6,"reason":"稍旧"},
                  {"key":"independence","score":8,"reason":"有新角度"},
                  {"key":"writability","score":7,"reason":"可引用"}
                ],"recommendUse":"priority","riskNote":""}]}
                """)
            .build());

    ResearchCandidate candidate = ResearchCandidate.builder()
        .title("测试标题")
        .url("https://example.com/a")
        .summary("摘要")
        .kind(ResearchItemKind.WEB)
        .build();
    service.scoreCandidates("选题", "摘要", List.of(candidate));

    assertThat(candidate.getScore()).isEqualTo(82);
    assertThat(candidate.getScoreDetails()).isNotNull();
    assertThat(candidate.getScoreDetails().getRecommendUse()).isEqualTo("priority");
    assertThat(candidate.getScoreDetails().getDimensions()).hasSize(5);
    assertThat(candidate.getScoreDetails().getDimensions().get(0).getScore()).isEqualTo(10);
  }

  @Test
  void scoreCandidates_AI失败回退默认分() {
    when(ai.completeText(any(TextCompleteRequest.class)))
        .thenThrow(new RuntimeException("timeout"));

    ResearchCandidate candidate = ResearchCandidate.builder()
        .title("测试")
        .url("https://example.com/b")
        .summary("")
        .kind(ResearchItemKind.WEB)
        .build();
    service.scoreCandidates("选题", "", List.of(candidate));

    assertThat(candidate.getScore()).isEqualTo(80);
    assertThat(candidate.getScoreDetails().getRiskNote()).contains("默认分");
  }

  @Test
  void scoreCandidates_无法解析JSON时保留默认分() {
    when(ai.completeText(any(TextCompleteRequest.class))).thenReturn(
        TextCompleteResult.builder().text("不是 JSON").build());

    ResearchCandidate candidate = ResearchCandidate.builder()
        .title("测试")
        .kind(ResearchItemKind.SOURCE)
        .build();
    service.scoreCandidates("选题", "", List.of(candidate));

    assertThat(candidate.getScore()).isEqualTo(70);
  }

  @Test
  void scoreCandidates_传入选题上下文() {
    ArgumentCaptor<TextCompleteRequest> captor =
        ArgumentCaptor.forClass(TextCompleteRequest.class);
    when(ai.completeText(captor.capture())).thenReturn(
        TextCompleteResult.builder().text("{\"items\":[]}").build());

    service.scoreCandidates(
        "中老年养生",
        "警惕排毒说法",
        List.of(ResearchCandidate.builder()
            .title("资料")
            .kind(ResearchItemKind.WEB)
            .build()));

    String prompt = captor.getValue().getPrompt();
    assertThat(prompt).contains("中老年养生");
    assertThat(prompt).contains("警惕排毒说法");
    assertThat(captor.getValue().getScene()).isEqualTo("hot_news_research_item_score");
  }
}
