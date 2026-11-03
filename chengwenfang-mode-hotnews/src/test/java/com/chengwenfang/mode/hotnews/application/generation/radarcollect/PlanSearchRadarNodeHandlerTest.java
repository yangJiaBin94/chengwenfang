package com.chengwenfang.mode.hotnews.application.generation.radarcollect;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.crawler.facade.dto.request.ChannelFetchConfig;
import com.chengwenfang.crawler.facade.enums.ChannelType;
import com.chengwenfang.mode.hotnews.application.search.HotNewsSearchChannelSelector;
import com.chengwenfang.sourcing.facade.dto.view.ChannelView;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 自适应搜索渠道选择（委托 Selector）。 */
class PlanSearchRadarNodeHandlerTest {

  private final HotNewsSearchChannelSelector selector = new HotNewsSearchChannelSelector();

  @Test
  void resolveOne_prefers_tavily_with_key_over_searxng() {
    ChannelView searxng = ChannelView.builder()
        .id("c-sx")
        .type(ChannelType.SEARXNG.getValue())
        .name("SearXNG")
        .build();
    ChannelView tavily = ChannelView.builder()
        .id("c-tv")
        .type(ChannelType.TAVILY.getValue())
        .name("Tavily")
        .config(ChannelFetchConfig.builder().apiKey("tvly-test").build())
        .build();
    assertThat(selector.resolveOne(List.of(searxng, tavily)))
        .extracting(ChannelView::getId)
        .isEqualTo("c-tv");
  }

  @Test
  void resolveOne_falls_back_to_searxng_without_tavily_key() {
    ChannelView searxng = ChannelView.builder()
        .id("c-sx")
        .type(ChannelType.SEARXNG.getValue())
        .name("SearXNG")
        .build();
    ChannelView tavily = ChannelView.builder()
        .id("c-tv")
        .type(ChannelType.TAVILY.getValue())
        .name("Tavily")
        .config(ChannelFetchConfig.builder().build())
        .build();
    assertThat(selector.resolveOne(List.of(tavily, searxng)))
        .extracting(ChannelView::getId)
        .isEqualTo("c-sx");
  }
}
