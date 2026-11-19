package com.chengwenfang.mode.hotnews.application.hotnews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chengwenfang.content.facade.constant.HotNewsThemeSeedChannelKeys;
import com.chengwenfang.crawler.facade.enums.ChannelType;
import com.chengwenfang.mode.hotnews.facade.HotNewsFacade;
import com.chengwenfang.sourcing.facade.SourceChannelCatalogFacade;
import com.chengwenfang.sourcing.facade.constant.SourceChannelCatalogCodes;
import com.chengwenfang.sourcing.facade.constant.SourceChannelCatalogTags;
import com.chengwenfang.sourcing.facade.dto.request.HotNewsChannelCreateRequest;
import com.chengwenfang.sourcing.facade.dto.view.HotNewsChannelView;
import com.chengwenfang.sourcing.facade.dto.view.SourceChannelCatalogView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** {@link HotNewsThemeChannelAutoWireSupport} 启用与保存配源。 */
class HotNewsThemeChannelAutoWireSupportTest {
  private HotNewsFacade hotNews;
  private SourceChannelCatalogFacade catalogFacade;
  private HotNewsThemeChannelAutoWireSupport support;

  @BeforeEach
  void setUp() {
    hotNews = mock(HotNewsFacade.class);
    catalogFacade = mock(SourceChannelCatalogFacade.class);
    support = new HotNewsThemeChannelAutoWireSupport(hotNews, catalogFacade);
    when(catalogFacade.listEnabledCatalog()).thenReturn(catalog());
    when(hotNews.createChannel(eq("theme-1"), any())).thenAnswer(invocation -> {
      HotNewsChannelCreateRequest req = invocation.getArgument(1);
      return HotNewsChannelView.builder()
          .id("new-" + req.getName())
          .name(req.getName())
          .type(req.getType())
          .baseUrl(req.getBaseUrl())
          .enabled(true)
          .build();
    });
  }

  @Test
  void wireOnCommit_createsSearxngAndMatchedCatalog() {
    when(hotNews.listChannels("theme-1")).thenReturn(List.of());

    HotNewsThemeChannelAutoWireSupport.AutoWireResult result =
        support.wireOnCommit("theme-1", aiBriefing());

    ArgumentCaptor<HotNewsChannelCreateRequest> captor =
        ArgumentCaptor.forClass(HotNewsChannelCreateRequest.class);
    verify(hotNews, org.mockito.Mockito.atLeast(2)).createChannel(eq("theme-1"), captor.capture());
    assertThat(captor.getAllValues())
        .extracting(HotNewsChannelCreateRequest::getName)
        .contains(
            HotNewsThemeSeedChannelKeys.SEARXNG_DISPLAY_NAME,
            "SoPilot AI 起爆帖");
    assertThat(result.seededKeys())
        .contains(HotNewsThemeSeedChannelKeys.SEARXNG, "catalog:" + SourceChannelCatalogCodes.SOPILOT_AI);
  }

  @Test
  void syncOnSaveConfig_keepsManualChannelAndDropsStaleCatalog() {
    String sopilotUrl = "https://sopilot.net/rss/hottweets";
    HotNewsChannelView searxng = HotNewsChannelView.builder()
        .id("ch-searx")
        .type(ChannelType.SEARXNG.getValue())
        .name(HotNewsThemeSeedChannelKeys.SEARXNG_DISPLAY_NAME)
        .baseUrl("https://searx.example")
        .enabled(false)
        .build();
    HotNewsChannelView catalogOwned = HotNewsChannelView.builder()
        .id("ch-sopilot")
        .type(ChannelType.RSS.getValue())
        .name("SoPilot AI 起爆帖")
        .baseUrl(sopilotUrl)
        .enabled(true)
        .build();
    HotNewsChannelView sameUrlManual = HotNewsChannelView.builder()
        .id("ch-manual-same-url")
        .type(ChannelType.RSS.getValue())
        .name("我手工加的同址源")
        .baseUrl(sopilotUrl)
        .enabled(true)
        .build();
    HotNewsChannelView manual = HotNewsChannelView.builder()
        .id("ch-manual")
        .type(ChannelType.RSS.getValue())
        .name("手工源")
        .baseUrl("https://example.com/feed.xml")
        .enabled(true)
        .build();
    when(hotNews.listChannels("theme-1"))
        .thenReturn(List.of(searxng, catalogOwned, sameUrlManual, manual));

    // 意图改为非 AI：只删名称+URL 双对齐的池托管；同 URL 改名手工源与普通手工源保留
    support.syncOnSaveConfig("theme-1", consumerBriefing());

    verify(hotNews).deleteChannel("ch-sopilot");
    verify(hotNews, never()).deleteChannel("ch-manual");
    verify(hotNews, never()).deleteChannel("ch-manual-same-url");
    // 不强制重新启用已停用的 SearXNG
    org.mockito.ArgumentCaptor<com.chengwenfang.sourcing.facade.dto.request.HotNewsChannelPatchRequest>
        patchCaptor =
            org.mockito.ArgumentCaptor.forClass(
                com.chengwenfang.sourcing.facade.dto.request.HotNewsChannelPatchRequest.class);
    verify(hotNews).patchChannel(eq("ch-searx"), patchCaptor.capture());
    assertThat(patchCaptor.getValue().getEnabled()).isNull();
  }

  private static Map<String, Object> aiBriefing() {
    Map<String, Object> naming = new LinkedHashMap<>();
    naming.put("name", "AI 主题");
    naming.put("description", "大模型");
    Map<String, Object> radar = new LinkedHashMap<>();
    radar.put("search_queries", List.of("Claude 发布"));
    Map<String, Object> intent = new LinkedHashMap<>();
    intent.put("summary", "抓 AI 发布");
    Map<String, Object> briefing = new LinkedHashMap<>();
    briefing.put("naming", naming);
    briefing.put("radar", radar);
    briefing.put("core_intent", intent);
    return briefing;
  }

  private static Map<String, Object> consumerBriefing() {
    Map<String, Object> naming = new LinkedHashMap<>();
    naming.put("name", "消费");
    naming.put("description", "品牌");
    Map<String, Object> radar = new LinkedHashMap<>();
    radar.put("search_queries", List.of("新品"));
    Map<String, Object> intent = new LinkedHashMap<>();
    intent.put("summary", "找消费案例");
    Map<String, Object> briefing = new LinkedHashMap<>();
    briefing.put("naming", naming);
    briefing.put("radar", radar);
    briefing.put("core_intent", intent);
    return briefing;
  }

  private static List<SourceChannelCatalogView> catalog() {
    return List.of(
        SourceChannelCatalogView.builder()
            .id("1")
            .code(SourceChannelCatalogCodes.SOPILOT_AI)
            .name("SoPilot AI 起爆帖")
            .channelType("rss")
            .baseUrl("https://sopilot.net/rss/hottweets")
            .tags(List.of(SourceChannelCatalogTags.AI))
            .sortOrder(10)
            .enabled(true)
            .build());
  }
}
