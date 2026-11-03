package com.chengwenfang.mode.hotnews.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.crawler.facade.enums.ChannelType;
import com.chengwenfang.mode.hotnews.application.search.HotNewsSearchChannelSelector;
import com.chengwenfang.sourcing.domain.entity.SourceChannelEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link HotNewsSearchChannelSelector#preferTavilyForResearch} 选路。 */
class ResearchCandidateCollectorPreferTavilyTest {

  private final HotNewsSearchChannelSelector selector = new HotNewsSearchChannelSelector();

  @Test
  void prefers_tavily_when_key_configured() {
    SourceChannelEntity searx = channel("SearXNG", ChannelType.SEARXNG.getValue(), null);
    SourceChannelEntity tavily = channel("Tavily", ChannelType.TAVILY.getValue(), "cipher");
    List<SourceChannelEntity> chosen = selector.preferTavilyForResearch(List.of(searx, tavily));
    assertThat(chosen).containsExactly(tavily);
    assertThat(selector.isTavilyOnly(chosen)).isTrue();
  }

  @Test
  void keeps_all_when_tavily_has_no_key() {
    SourceChannelEntity searx = channel("SearXNG", ChannelType.SEARXNG.getValue(), null);
    SourceChannelEntity tavily = channel("Tavily", ChannelType.TAVILY.getValue(), "  ");
    List<SourceChannelEntity> chosen = selector.preferTavilyForResearch(List.of(searx, tavily));
    assertThat(chosen).containsExactly(searx, tavily);
    assertThat(selector.isTavilyOnly(chosen)).isFalse();
  }

  @Test
  void empty_input_stays_empty() {
    assertThat(selector.preferTavilyForResearch(List.of())).isEmpty();
    assertThat(selector.preferTavilyForResearch(null)).isEmpty();
  }

  private static SourceChannelEntity channel(String name, String type, String apiKeyCiphertext) {
    SourceChannelEntity entity = new SourceChannelEntity();
    entity.setName(name);
    entity.setChannelType(type);
    entity.setEnabled(true);
    entity.setApiKeyCiphertext(apiKeyCiphertext);
    return entity;
  }
}
