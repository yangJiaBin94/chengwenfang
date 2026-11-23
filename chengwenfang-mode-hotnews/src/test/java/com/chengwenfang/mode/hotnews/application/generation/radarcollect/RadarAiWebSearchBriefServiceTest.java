package com.chengwenfang.mode.hotnews.application.generation.radarcollect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chengwenfang.ai.facade.AiVisionFacade;
import com.chengwenfang.ai.facade.dto.request.VisionCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.VisionCompleteResult;
import com.chengwenfang.common.ThemeTitleRelevance;
import com.chengwenfang.content.facade.dto.view.ScoringRubricDimensionView;
import com.chengwenfang.content.facade.dto.view.ScoringRubricView;
import com.chengwenfang.mode.hotnews.application.generation.radarcollect.model.RadarAiWebSearchBriefResult;
import com.chengwenfang.mode.hotnews.application.hotnews.TopicAiWritingScoreService;
import com.chengwenfang.mode.hotnews.application.prompt.RadarAiWebSearchBriefPrompts;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link RadarAiWebSearchBriefService} 解析与生成回退测试。 */
class RadarAiWebSearchBriefServiceTest {

  @Test
  void parse_rejectsBlankOrShortBrief() {
    assertThat(RadarAiWebSearchBriefService.parse(null)).isNull();
    assertThat(RadarAiWebSearchBriefService.parse("")).isNull();
    assertThat(RadarAiWebSearchBriefService.parse("{\"searchBrief\":\"太短\"}")).isNull();
  }

  @Test
  void parse_acceptsValidBriefAndFocusDimensions() {
    String json = """
        {
          "searchBrief":"请围绕人工智能主题，在近24小时内搜索一手发布与权威报道，时效维度权重最高，无明确发布时间的材料宁缺毋滥，给出可打开原文链接。",
          "focusDimensions":["时效性","一手性","时效性"]
        }
        """;
    RadarAiWebSearchBriefResult result = RadarAiWebSearchBriefService.parse(json);
    assertThat(result).isNotNull();
    assertThat(result.isFromAi()).isTrue();
    assertThat(result.getSearchBrief()).contains("近24小时");
    assertThat(result.getFocusDimensions()).containsExactly("时效性", "一手性");
  }

  @Test
  void buildUserFacts_includesRubricWeights() {
    RadarCollectSessionStore.Session session = new RadarCollectSessionStore.Session();
    session.setThemeId("theme-1");
    session.setHours(24);
    session.setCoreIntentPrompt("关注模型发布与成本下降");
    session.setThemeRelevanceProfile(new ThemeTitleRelevance.Profile(
        "AI 观察",
        List.of("大模型", "推理成本"),
        List.of("OpenAI", "通义")));
    ScoringRubricView rubric = ScoringRubricView.builder()
        .version(1)
        .scoreGuidance("优先一手与时效")
        .dimensions(List.of(
            ScoringRubricDimensionView.builder()
                .key("freshness")
                .label("时效性")
                .description("是否在窗口内")
                .weight(40)
                .build(),
            ScoringRubricDimensionView.builder()
                .key("theme_fit")
                .label("主题契合")
                .description("是否贴合意图")
                .weight(35)
                .build(),
            ScoringRubricDimensionView.builder()
                .key("source_trust")
                .label("来源可信")
                .description("是否一手权威")
                .weight(25)
                .build()))
        .build();
    String facts = RadarAiWebSearchBriefService.buildUserFacts(session, rubric);
    assertThat(facts).contains("AI 观察");
    assertThat(facts).contains("回溯小时数：24（仅作参考，以绝对日期窗为准）");
    assertThat(facts).contains("时效硬开关：开");
    assertThat(facts).contains("时效硬窗口天数：7");
    assertThat(facts).contains("今天（服务器日期）");
    assertThat(facts).contains("时效窗口下限");
    assertThat(facts).contains("早于窗口下限的任何日期一律视为超窗丢弃");
    assertThat(facts).contains("weight=40%");
    assertThat(facts).contains("时效性");
    assertThat(facts).contains("量表指引：优先一手与时效");
  }

  @Test
  void briefSystemPrompt_requiresShortQueriesWithDateAnchor() {
    assertThat(RadarAiWebSearchBriefPrompts.SYSTEM_PROMPT)
        .contains("3～6 条")
        .contains("after:YYYY-MM-DD")
        .contains("窗内首发日")
        .contains("禁止用旧闻凑数");
  }

  @Test
  void generate_returnsAiBrief_whenModelOk() {
    AiVisionFacade ai = mock(AiVisionFacade.class);
    TopicAiWritingScoreService writingScore = mock(TopicAiWritingScoreService.class);
    when(writingScore.loadRubric("theme-1")).thenReturn(null);
    when(ai.complete(any(VisionCompleteRequest.class))).thenReturn(VisionCompleteResult.builder()
        .text("""
            {"searchBrief":"请围绕主题联网搜索近24小时内的一手资讯与权威报道，给出可打开链接并说明可写理由，时效优先。",
             "focusDimensions":["时效性"]}
            """)
        .build());
    RadarAiWebSearchBriefService service = new RadarAiWebSearchBriefService(ai, writingScore);
    RadarCollectSessionStore.Session session = new RadarCollectSessionStore.Session();
    session.setThemeId("theme-1");
    session.setHours(24);
    RadarAiWebSearchBriefResult result = service.generate(session);
    assertThat(result).isNotNull();
    assertThat(result.isFromAi()).isTrue();
    assertThat(result.getSearchBrief()).contains("近24小时");
  }

  @Test
  void generate_returnsNull_whenModelFails() {
    AiVisionFacade ai = mock(AiVisionFacade.class);
    TopicAiWritingScoreService writingScore = mock(TopicAiWritingScoreService.class);
    when(writingScore.loadRubric("theme-1")).thenReturn(null);
    when(ai.complete(any(VisionCompleteRequest.class))).thenThrow(new RuntimeException("boom"));
    RadarAiWebSearchBriefService service = new RadarAiWebSearchBriefService(ai, writingScore);
    RadarCollectSessionStore.Session session = new RadarCollectSessionStore.Session();
    session.setThemeId("theme-1");
    assertThat(service.generate(session)).isNull();
  }
}
