package com.chengwenfang.mode.hotnews.application.hotnews;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.sourcing.facade.constant.SourceChannelCatalogCodes;
import com.chengwenfang.sourcing.facade.constant.SourceChannelCatalogTags;
import com.chengwenfang.sourcing.facade.dto.view.SourceChannelCatalogView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@link HotNewsChannelCatalogMatcher} 标签交集。 */
class HotNewsChannelCatalogMatcherTest {

  @Test
  void match_returnsAiCatalogForAiTheme() {
    List<SourceChannelCatalogView> matched =
        HotNewsChannelCatalogMatcher.match(aiBriefing(), catalog(), 4);
    assertThat(matched).extracting(SourceChannelCatalogView::getCode)
        .containsExactly(SourceChannelCatalogCodes.SOPILOT_AI);
  }

  @Test
  void match_returnsEmptyWhenMaxMatchesNonPositive() {
    assertThat(HotNewsChannelCatalogMatcher.match(aiBriefing(), catalog(), 0)).isEmpty();
    assertThat(HotNewsChannelCatalogMatcher.match(aiBriefing(), catalog(), -1)).isEmpty();
  }

  @Test
  void match_returnsEmptyWhenNoThemeTags() {
    Map<String, Object> naming = new LinkedHashMap<>();
    naming.put("name", "消费观察");
    naming.put("description", "新品");
    Map<String, Object> intent = new LinkedHashMap<>();
    intent.put("summary", "找消费案例");
    Map<String, Object> briefing = new LinkedHashMap<>();
    briefing.put("naming", naming);
    briefing.put("core_intent", intent);
    assertThat(HotNewsChannelCatalogMatcher.match(briefing, catalog(), 4)).isEmpty();
  }

  @Test
  void match_canHitBothWhenThemeCoversAiAndWeb3() {
    Map<String, Object> naming = new LinkedHashMap<>();
    naming.put("name", "AI 与 Web3");
    naming.put("description", "大模型与区块链交叉");
    Map<String, Object> intent = new LinkedHashMap<>();
    intent.put("summary", "同时盯 AI 与 Web3 热点");
    Map<String, Object> briefing = new LinkedHashMap<>();
    briefing.put("naming", naming);
    briefing.put("core_intent", intent);
    assertThat(HotNewsChannelCatalogMatcher.match(briefing, catalog(), 4))
        .extracting(SourceChannelCatalogView::getCode)
        .containsExactly(
            SourceChannelCatalogCodes.SOPILOT_AI, SourceChannelCatalogCodes.SOPILOT_WEB3);
  }

  private static Map<String, Object> aiBriefing() {
    Map<String, Object> naming = new LinkedHashMap<>();
    naming.put("name", "AI 雷达");
    naming.put("description", "大模型");
    Map<String, Object> intent = new LinkedHashMap<>();
    intent.put("summary", "抓 AI 发布");
    Map<String, Object> briefing = new LinkedHashMap<>();
    briefing.put("naming", naming);
    briefing.put("core_intent", intent);
    return briefing;
  }

  private static List<SourceChannelCatalogView> catalog() {
    return List.of(
        SourceChannelCatalogView.builder()
            .id("1")
            .code(SourceChannelCatalogCodes.SOPILOT_AI)
            .name("SoPilot AI")
            .channelType("rss")
            .baseUrl("https://sopilot.net/rss/hottweets")
            .tags(List.of(SourceChannelCatalogTags.AI))
            .sortOrder(10)
            .enabled(true)
            .build(),
        SourceChannelCatalogView.builder()
            .id("2")
            .code(SourceChannelCatalogCodes.SOPILOT_WEB3)
            .name("SoPilot Web3")
            .channelType("rss")
            .baseUrl("https://sopilot.net/rss/hottweets?type=Web3")
            .tags(List.of(SourceChannelCatalogTags.WEB3))
            .sortOrder(20)
            .enabled(true)
            .build());
  }
}
