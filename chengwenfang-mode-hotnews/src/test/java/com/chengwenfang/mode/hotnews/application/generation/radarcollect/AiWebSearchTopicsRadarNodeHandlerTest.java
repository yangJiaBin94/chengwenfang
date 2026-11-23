package com.chengwenfang.mode.hotnews.application.generation.radarcollect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.chengwenfang.ai.facade.AiVisionFacade;
import com.chengwenfang.ai.facade.dto.view.WebSearchCitation;
import com.chengwenfang.job.facade.JobFacade;
import com.chengwenfang.mode.hotnews.application.generation.radarcollect.model.AiWebSearchTopicAiResult;
import com.chengwenfang.mode.hotnews.application.generation.radarcollect.model.RadarAiWebSearchBriefResult;
import com.chengwenfang.mode.hotnews.facade.HotNewsFacade;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link AiWebSearchTopicsRadarNodeHandler} 搜索提示与发布日解析测试。 */
class AiWebSearchTopicsRadarNodeHandlerTest {

  @Test
  void parsePublishedAt_acceptsIsoAndChineseDate() {
    assertThat(AiWebSearchTopicsRadarNodeHandler.parsePublishedAt(null)).isNull();
    assertThat(AiWebSearchTopicsRadarNodeHandler.parsePublishedAt("")).isNull();
    assertThat(AiWebSearchTopicsRadarNodeHandler.parsePublishedAt("不是日期")).isNull();
    assertThat(AiWebSearchTopicsRadarNodeHandler.parsePublishedAt("2026-11-20"))
        .isEqualTo(LocalDateTime.of(2026, 11, 20, 0, 0));
    assertThat(AiWebSearchTopicsRadarNodeHandler.parsePublishedAt("2026年11月20日"))
        .isEqualTo(LocalDateTime.of(2026, 11, 20, 0, 0));
  }

  @Test
  void resolveSearchPrompt_prefersAiBrief() {
    RadarCollectSessionStore.Session session = new RadarCollectSessionStore.Session();
    session.setHours(24);
    RadarAiWebSearchBriefResult brief = RadarAiWebSearchBriefResult.builder()
        .fromAi(true)
        .searchBrief("按时效性高权重：只找近24小时一手报道，无日期慎选。")
        .build();
    assertThat(AiWebSearchTopicsRadarNodeHandler.resolveSearchPrompt(brief, session))
        .isEqualTo(brief.getSearchBrief());
  }

  @Test
  void resolveSearchPrompt_fallsBackSoftWhenFreshnessOff() {
    RadarCollectSessionStore.Session session = new RadarCollectSessionStore.Session();
    session.setHours(12);
    session.setRequireTopicFreshness(false);
    session.setCoreIntentPrompt("测意图");
    String fallback = AiWebSearchTopicsRadarNodeHandler.resolveSearchPrompt(null, session);
    assertThat(fallback).contains("回溯约 12 小时内优先");
    assertThat(fallback).contains("测意图");
  }

  @Test
  void resolveSearchPrompt_fallsBackHardSevenDaysWhenFreshnessOn() {
    RadarCollectSessionStore.Session session = new RadarCollectSessionStore.Session();
    session.setRequireTopicFreshness(true);
    String fallback = AiWebSearchTopicsRadarNodeHandler.resolveSearchPrompt(null, session);
    assertThat(fallback).contains("今天（服务器日期）");
    assertThat(fallback).contains("时效窗口下限");
    assertThat(fallback).contains("3～6 条");
    assertThat(fallback).contains("after:");
    assertThat(fallback).contains("禁止用旧闻凑数");
    assertThat(fallback).contains("窗内首发日");
    assertThat(fallback).doesNotContain("最新高热度");
  }

  @Test
  void webSearchSystem_requiresQueryDateAnchorWhenFreshnessOn() {
    String system = AiWebSearchTopicsRadarNodeHandler.webSearchSystem(true);
    assertThat(system).contains("after:");
    assertThat(system).contains("窗内首发日");
    assertThat(system).contains("禁止用旧闻凑条数");
    assertThat(AiWebSearchTopicsRadarNodeHandler.structureSystem(true))
        .contains("禁止用旧闻凑数");
  }

  @Test
  void withAbsoluteFreshnessAnchor_prependsServerDatesWhenOn() {
    String wrapped = AiWebSearchTopicsRadarNodeHandler.withAbsoluteFreshnessAnchor("搜热点", true);
    assertThat(wrapped).startsWith("今天（服务器日期）");
    assertThat(wrapped).contains("搜热点");
    assertThat(wrapped).contains("早于窗口下限的任何日期一律视为超窗丢弃");
    assertThat(wrapped).doesNotContain("2025 年及更早");
    assertThat(AiWebSearchTopicsRadarNodeHandler.withAbsoluteFreshnessAnchor("搜热点", false))
        .isEqualTo("搜热点");
  }

  @Test
  void appendCandidates_skipsStaleWhenFreshnessRequired() {
    AiWebSearchTopicsRadarNodeHandler handler = new AiWebSearchTopicsRadarNodeHandler(
        mock(AiVisionFacade.class),
        mock(HotNewsFacade.class),
        mock(JobFacade.class),
        mock(RadarAiWebSearchBriefService.class));
    RadarCollectSessionStore.Session session = new RadarCollectSessionStore.Session();
    session.setThemeId("theme-1");
    session.setRequireTopicFreshness(true);
    String freshDate = LocalDate.now().minusDays(2).toString();
    String staleDate = LocalDate.now().minusDays(30).toString();
    String freshUrl = "https://example.com/fresh";
    String staleUrl = "https://example.com/stale";
    List<AiWebSearchTopicAiResult> topics = List.of(
        AiWebSearchTopicAiResult.builder()
            .title("新")
            .url(freshUrl)
            .summary("s")
            .publishedAt(freshDate)
            .build(),
        AiWebSearchTopicAiResult.builder()
            .title("旧")
            .url(staleUrl)
            .summary("s")
            .publishedAt(staleDate)
            .build());
    List<WebSearchCitation> citations = List.of(
        WebSearchCitation.builder().url(freshUrl).title("新").build(),
        WebSearchCitation.builder().url(staleUrl).title("旧").build());
    AiWebSearchTopicsRadarNodeHandler.AppendOutcome outcome =
        handler.appendCandidates(session, topics, citations, "channel-1");
    assertThat(outcome.added()).isEqualTo(1);
    assertThat(outcome.skippedStale()).isEqualTo(1);
    assertThat(session.getCandidates()).hasSize(1);
    assertThat(session.getCandidates().get(0).getUrl()).isEqualTo(freshUrl);
  }
}
