package com.workbench.mode.hotnews.application.hotnews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.workbench.ai.facade.AiTextFacade;
import com.workbench.ai.facade.dto.request.TextCompleteRequest;
import com.workbench.ai.facade.dto.view.TextCompleteResult;
import com.workbench.content.facade.ContentFacade;
import com.workbench.content.facade.dto.view.ContentThemeView;
import com.workbench.crawler.facade.FetchFacade;
import com.workbench.crawler.facade.dto.request.ChannelFetchConfig;
import com.workbench.crawler.facade.dto.request.FetchRequest;
import com.workbench.crawler.facade.dto.view.FetchResult;
import com.workbench.crawler.facade.enums.ChannelType;
import com.workbench.mode.hotnews.facade.HotNewsFacade;
import com.workbench.sourcing.facade.dto.request.HotNewsChannelCreateRequest;
import com.workbench.sourcing.facade.dto.view.HotNewsChannelView;
import com.workbench.mode.hotnews.facade.dto.view.HotNewsSourceChannelGenerateView;
import com.workbench.skill.facade.SkillFacade;
import com.workbench.skill.facade.constant.HotNewsSkillNames;
import com.workbench.skill.facade.constant.HotNewsWorkflowKeys;
import com.workbench.skill.facade.dto.view.SkillView;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@link HotNewsThemeSourceChannelGenerateService}：解析、去重、网页 URL 过滤与落库。
 */
class HotNewsThemeSourceChannelGenerateServiceTest {
  private AiTextFacade ai;
  private SkillFacade skills;
  private ContentFacade content;
  private HotNewsFacade hotNews;
  private FetchFacade fetchFacade;
  private HotNewsThemeSourceChannelGenerateService service;

  @BeforeEach
  void setUp() {
    ai = mock(AiTextFacade.class);
    skills = mock(SkillFacade.class);
    content = mock(ContentFacade.class);
    hotNews = mock(HotNewsFacade.class);
    fetchFacade = mock(FetchFacade.class);
    ChannelSuggestionPromptBuilder prompts = new ChannelSuggestionPromptBuilder(ai, skills);
    ChannelSuggestionValidator validator =
        new ChannelSuggestionValidator(hotNews, fetchFacade);
    ChannelSuggestionPersister persister =
        new ChannelSuggestionPersister(content, hotNews, prompts, validator);
    service = new HotNewsThemeSourceChannelGenerateService(persister, validator);
    when(skills.listSkills()).thenReturn(List.of(SkillView.builder()
        .name(HotNewsSkillNames.THEME_SOURCE_CHANNELS)
        .scene(HotNewsWorkflowKeys.SCENE)
        .enabled(true)
        .systemInstructions("你是来源渠道推荐助手，只返回合法 JSON。")
        .build()));
  }

  @Test
  void parseCandidates_readsChannelsJson() {
    List<ChannelSuggestionParser.AiChannelCandidate> parsed =
        HotNewsThemeSourceChannelGenerateService.parseCandidates("""
            ```json
            {"channels":[{"name":"BBC 健康","type":"rss","baseUrl":"https://feeds.bbci.co.uk/news/health/rss.xml","config":{}}]}
            ```
            """);
    assertThat(parsed).hasSize(1);
    assertThat(parsed.get(0).name()).isEqualTo("BBC 健康");
    assertThat(parsed.get(0).type()).isEqualTo("rss");
    assertThat(parsed.get(0).baseUrl()).contains("bbci.co.uk");
  }

  @Test
  void dedupeAndCapWebUrls_removesDupesAndCapsAtFive() {
    List<String> capped = HotNewsThemeSourceChannelGenerateService.dedupeAndCapWebUrls(List.of(
        "https://a.example/x",
        "https://a.example/x/",
        "https://b.example/1",
        "https://c.example/1",
        "https://d.example/1",
        "https://e.example/1",
        "https://f.example/1"));
    assertThat(capped).hasSize(HotNewsThemeSourceChannelGenerateService.MAX_WEB_URLS);
    assertThat(capped.get(0)).isEqualTo("https://a.example/x");
    assertThat(capped).doesNotContain("https://f.example/1");
  }

  @Test
  void parseCandidates_capsChannelsAndWebUrls() {
    StringBuilder json = new StringBuilder("{\"channels\":[");
    for (int index = 1; index <= 8; index++) {
      if (index > 1) {
        json.append(',');
      }
      json.append("{\"name\":\"c").append(index)
          .append("\",\"type\":\"rss\",\"baseUrl\":\"https://example.com/feed")
          .append(index).append(".xml\",\"config\":{}}");
    }
    json.append("]}");
    List<ChannelSuggestionParser.AiChannelCandidate> channels =
        HotNewsThemeSourceChannelGenerateService.parseCandidates(json.toString());
    assertThat(channels).hasSize(HotNewsThemeSourceChannelGenerateService.MAX_CHANNELS);

    List<ChannelSuggestionParser.AiChannelCandidate> web =
        HotNewsThemeSourceChannelGenerateService.parseCandidates("""
            {"channels":[{"name":"网页","type":"web","baseUrl":"https://a.example/1",
            "config":{"urls":[
              "https://a.example/1","https://a.example/1/",
              "https://b.example/2","https://c.example/3","https://d.example/4",
              "https://e.example/5","https://f.example/6"
            ]}}]}
            """);
    assertThat(web).hasSize(1);
    assertThat(web.get(0).config().getUrls())
        .hasSize(HotNewsThemeSourceChannelGenerateService.MAX_WEB_URLS)
        .doesNotContain("https://f.example/6");
  }

  @Test
  void generate_keepsOnlyNewWebUrlsAgainstExisting() {
    stubTheme();
    when(hotNews.listChannels("theme-1")).thenReturn(List.of(
        HotNewsChannelView.builder()
            .id("web-1")
            .name("已有网页")
            .type(ChannelType.WEB.getValue())
            .baseUrl("https://ok.example/old")
            .config(ChannelFetchConfig.builder()
                .urls(List.of("https://ok.example/old"))
                .build())
            .build()));
    when(ai.completeText(any(TextCompleteRequest.class))).thenReturn(TextCompleteResult.builder()
        .text("""
            {"channels":[{"name":"补充网页","type":"web","baseUrl":"https://ok.example/old",
            "config":{"urls":["https://ok.example/old","https://ok.example/old/",
            "https://ok.example/new"]}}]}
            """)
        .build());
    when(fetchFacade.testConnection(any(FetchRequest.class)))
        .thenReturn(FetchResult.builder().ok(true).build());
    when(hotNews.createChannel(eq("theme-1"), any(HotNewsChannelCreateRequest.class)))
        .thenAnswer(invocation -> {
          HotNewsChannelCreateRequest body = invocation.getArgument(1);
          return HotNewsChannelView.builder()
              .id("ch-new")
              .name(body.getName())
              .type(body.getType())
              .baseUrl(body.getBaseUrl())
              .config(body.getConfig())
              .enabled(true)
              .build();
        });

    HotNewsSourceChannelGenerateView view = service.generate("theme-1");

    assertThat(view.getCreated()).hasSize(1);
    assertThat(view.getCreated().get(0).getConfig().getUrls())
        .containsExactly("https://ok.example/new");
    ArgumentCaptor<FetchRequest> fetchCaptor = ArgumentCaptor.forClass(FetchRequest.class);
    verify(fetchFacade).testConnection(fetchCaptor.capture());
    assertThat(fetchCaptor.getValue().getUrl()).isEqualTo("https://ok.example/new");
  }

  @Test
  void generate_persistsOnlyReachableWebUrls() {
    stubTheme();
    when(hotNews.listChannels("theme-1")).thenReturn(List.of());
    when(ai.completeText(any(TextCompleteRequest.class))).thenReturn(TextCompleteResult.builder()
        .text("""
            {"channels":[{"name":"健康网页","type":"web","baseUrl":"https://ok.example/a",
            "config":{"urls":["https://ok.example/a","https://bad.example/b"]}}]}
            """)
        .build());
    when(fetchFacade.testConnection(any(FetchRequest.class))).thenAnswer(invocation -> {
      FetchRequest request = invocation.getArgument(0);
      boolean ok = request.getUrl() != null && request.getUrl().contains("ok.example");
      return FetchResult.builder().ok(ok).build();
    });
    when(hotNews.createChannel(eq("theme-1"), any(HotNewsChannelCreateRequest.class)))
        .thenAnswer(invocation -> {
          HotNewsChannelCreateRequest body = invocation.getArgument(1);
          return HotNewsChannelView.builder()
              .id("ch-1")
              .themeId("theme-1")
              .name(body.getName())
              .type(body.getType())
              .baseUrl(body.getBaseUrl())
              .config(body.getConfig())
              .enabled(true)
              .build();
        });

    HotNewsSourceChannelGenerateView view = service.generate("theme-1");

    assertThat(view.isFromAi()).isTrue();
    assertThat(view.getCreated()).hasSize(1);
    assertThat(view.getCreated().get(0).getBaseUrl()).isEqualTo("https://ok.example/a");
    assertThat(view.getCreated().get(0).getConfig().getUrls()).containsExactly("https://ok.example/a");
    assertThat(view.getSkipped()).isEmpty();

    ArgumentCaptor<HotNewsChannelCreateRequest> captor =
        ArgumentCaptor.forClass(HotNewsChannelCreateRequest.class);
    verify(hotNews).createChannel(eq("theme-1"), captor.capture());
    assertThat(captor.getValue().getConfig().getUrls()).doesNotContain("https://bad.example/b");
  }

  @Test
  void generate_skipsWhenAllWebUrlsFail() {
    stubTheme();
    when(hotNews.listChannels("theme-1")).thenReturn(List.of());
    when(ai.completeText(any(TextCompleteRequest.class))).thenReturn(TextCompleteResult.builder()
        .text("""
            {"channels":[{"name":"坏网页","type":"web","baseUrl":"https://bad.example",
            "config":{"urls":["https://bad.example"]}}]}
            """)
        .build());
    when(fetchFacade.testConnection(any(FetchRequest.class)))
        .thenReturn(FetchResult.builder().ok(false).errorMessage("down").build());

    HotNewsSourceChannelGenerateView view = service.generate("theme-1");

    assertThat(view.isFromAi()).isTrue();
    assertThat(view.getCreated()).isEmpty();
    assertThat(view.getSkipped()).hasSize(1);
    assertThat(view.getSkipped().get(0).getReason())
        .isEqualTo(HotNewsSourceChannelSkipReasons.NO_VALID_URL);
    verify(hotNews, never()).createChannel(any(), any());
  }

  @Test
  void generate_dedupesExistingRss() {
    stubTheme();
    when(hotNews.listChannels("theme-1")).thenReturn(List.of(
        HotNewsChannelView.builder()
            .id("exist")
            .name("已有 BBC")
            .type(ChannelType.RSS.getValue())
            .baseUrl("https://feeds.bbci.co.uk/news/health/rss.xml")
            .build()));
    when(ai.completeText(any(TextCompleteRequest.class))).thenReturn(TextCompleteResult.builder()
        .text("""
            {"channels":[{"name":"BBC 健康","type":"rss",
            "baseUrl":"https://feeds.bbci.co.uk/news/health/rss.xml/","config":{}}]}
            """)
        .build());

    HotNewsSourceChannelGenerateView view = service.generate("theme-1");

    assertThat(view.isFromAi()).isTrue();
    assertThat(view.getCreated()).isEmpty();
    assertThat(view.getSkipped()).hasSize(1);
    assertThat(view.getSkipped().get(0).getReason())
        .isEqualTo(HotNewsSourceChannelSkipReasons.DUPLICATE);
    verify(fetchFacade, never()).testConnection(any());
    verify(hotNews, never()).createChannel(any(), any());
  }

  @Test
  void generate_skipsWebWhenAllUrlsAlreadyExist() {
    stubTheme();
    when(hotNews.listChannels("theme-1")).thenReturn(List.of(
        HotNewsChannelView.builder()
            .id("web-1")
            .name("已有网页")
            .type(ChannelType.WEB.getValue())
            .baseUrl("https://ok.example/a")
            .config(ChannelFetchConfig.builder()
                .urls(List.of("https://ok.example/a", "https://ok.example/b"))
                .build())
            .build()));
    when(ai.completeText(any(TextCompleteRequest.class))).thenReturn(TextCompleteResult.builder()
        .text("""
            {"channels":[{"name":"重复网页","type":"web","baseUrl":"https://ok.example/a",
            "config":{"urls":["https://ok.example/a","https://ok.example/b"]}}]}
            """)
        .build());

    HotNewsSourceChannelGenerateView view = service.generate("theme-1");

    assertThat(view.getCreated()).isEmpty();
    assertThat(view.getSkipped()).hasSize(1);
    assertThat(view.getSkipped().get(0).getReason())
        .isEqualTo(HotNewsSourceChannelSkipReasons.DUPLICATE);
    verify(fetchFacade, never()).testConnection(any());
    verify(hotNews, never()).createChannel(any(), any());
  }

  @Test
  void generate_skipsWhenThemeChannelLimitReached() {
    stubTheme();
    List<HotNewsChannelView> existing = new java.util.ArrayList<>();
    for (int index = 1; index <= 10; index++) {
      existing.add(HotNewsChannelView.builder()
          .id("ch-" + index)
          .name("已有" + index)
          .type(ChannelType.RSS.getValue())
          .baseUrl("https://example.com/feed" + index + ".xml")
          .enabled(true)
          .build());
    }
    when(hotNews.listChannels("theme-1")).thenReturn(existing);
    when(ai.completeText(any(TextCompleteRequest.class))).thenReturn(TextCompleteResult.builder()
        .text("""
            {"channels":[{"name":"超限","type":"rss","baseUrl":"https://example.com/new.xml","config":{}}]}
            """)
        .build());

    HotNewsSourceChannelGenerateView view = service.generate("theme-1");

    assertThat(view.getCreated()).isEmpty();
    assertThat(view.getSkipped()).isNotEmpty();
    assertThat(view.getSkipped().get(0).getReason())
        .isEqualTo(HotNewsSourceChannelSkipReasons.THEME_CHANNEL_LIMIT);
    verify(hotNews, never()).createChannel(any(), any());
  }

  @Test
  void generate_skipsSecondSearxngWhenExists() {
    stubTheme();
    when(hotNews.listChannels("theme-1")).thenReturn(List.of(
        HotNewsChannelView.builder()
            .id("sx")
            .name("SearXNG 搜索")
            .type(ChannelType.SEARXNG.getValue())
            .baseUrl("http://127.0.0.1:8080")
            .build()));
    when(ai.completeText(any(TextCompleteRequest.class))).thenReturn(TextCompleteResult.builder()
        .text("""
            {"channels":[{"name":"再来一个搜索","type":"searxng","baseUrl":"http://127.0.0.1:8080",
            "config":{"queries":["中老年养生"]}}]}
            """)
        .build());

    HotNewsSourceChannelGenerateView view = service.generate("theme-1");

    assertThat(view.getSkipped()).hasSize(1);
    assertThat(view.getSkipped().get(0).getReason())
        .isEqualTo(HotNewsSourceChannelSkipReasons.SEARXNG_EXISTS);
    verify(hotNews, never()).createChannel(any(), any());
  }

  @Test
  void generate_returnsFromAiFalseWhenModelEmpty() {
    stubTheme();
    when(hotNews.listChannels("theme-1")).thenReturn(List.of());
    when(ai.completeText(any(TextCompleteRequest.class)))
        .thenReturn(TextCompleteResult.builder().text("not json").build());

    HotNewsSourceChannelGenerateView view = service.generate("theme-1");

    assertThat(view.isFromAi()).isFalse();
    assertThat(view.getCreated()).isEmpty();
  }

  @Test
  void processCandidate_rssRequiresConnectivity() {
    ChannelSuggestionExistingIndex index = ChannelSuggestionExistingIndex.from(List.of());
    ChannelSuggestionParser.AiChannelCandidate candidate =
        new ChannelSuggestionParser.AiChannelCandidate(
            "BBC",
            "rss",
            "https://feeds.bbci.co.uk/news/health/rss.xml",
            ChannelFetchConfig.builder().build());
    when(fetchFacade.testConnection(any(FetchRequest.class)))
        .thenReturn(FetchResult.builder().ok(true).build());
    when(hotNews.createChannel(eq("theme-1"), any(HotNewsChannelCreateRequest.class)))
        .thenReturn(HotNewsChannelView.builder().id("n1").name("BBC").type("rss").build());

    ChannelSuggestionProcessResult result = service.processCandidate(
        "theme-1", candidate, index, Set.of(), List.of(), "");

    assertThat(result.created()).isNotNull();
    assertThat(result.skipped()).isNull();
  }

  private void stubTheme() {
    when(content.getTheme("theme-1")).thenReturn(ContentThemeView.builder()
        .id("theme-1")
        .name("中老年身心健康")
        .description("科学养生")
        .briefingJson("""
            {"naming":{"name":"中老年身心健康","description":"科学养生"},
             "radar":{"search_queries":["中老年科学养生方法","中老年心理健康维护"]}}
            """)
        .build());
  }
}
