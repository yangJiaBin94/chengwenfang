package com.chengwenfang.mode.hotnews.application.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.crawler.facade.constant.UpstreamJsonFields;
import com.chengwenfang.crawler.facade.dto.request.ChannelFetchConfig;
import com.chengwenfang.crawler.facade.enums.TavilyTopic;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsResearchSourceDefaults;
import com.chengwenfang.mode.hotnews.facade.enums.TavilySearchScene;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link TavilySearchConfigAssembler} 场景默认。 */
class TavilySearchConfigAssemblerTest {

  private final TavilySearchConfigAssembler assembler = new TavilySearchConfigAssembler();

  @Test
  void topic_research_sets_week_and_merges_domains() {
    ChannelFetchConfig out = assembler.assemble(
        ChannelFetchConfig.builder().topic("news").build(),
        TavilySearchContext.builder()
            .scene(TavilySearchScene.TOPIC_RESEARCH)
            .includeDomains(List.of("eastmoney.com"))
            .excludeDomains(List.of("spam.example"))
            .queries(List.of("皇冠新材 上市"))
            .financeTopic(true)
            .build());
    assertThat(out.getTimeRange()).isEqualTo(UpstreamJsonFields.TIME_RANGE_WEEK);
    assertThat(out.getIncludeRawContent()).isTrue();
    assertThat(out.getTopic()).isEqualTo(TavilyTopic.FINANCE.getValue());
    assertThat(out.getIncludeDomains()).contains("eastmoney.com");
    assertThat(out.getExcludeDomains())
        .containsAll(HotNewsResearchSourceDefaults.DEFAULT_BLOCKED_DOMAINS)
        .contains("spam.example");
    assertThat(out.getQueries()).containsExactly("皇冠新材 上市");
  }

  @Test
  void radar_fixed_maps_hours_to_day() {
    ChannelFetchConfig out = assembler.assemble(
        null,
        TavilySearchContext.builder()
            .scene(TavilySearchScene.RADAR_FIXED)
            .hours(12)
            .build());
    assertThat(out.getTimeRange()).isEqualTo(UpstreamJsonFields.TIME_RANGE_DAY);
    assertThat(out.getExcludeDomains()).contains("bilibili.com");
  }

  @Test
  void truncates_long_query() {
    String longQuery = "x".repeat(500);
    ChannelFetchConfig out = assembler.assemble(
        ChannelFetchConfig.builder().build(),
        TavilySearchContext.builder()
            .scene(TavilySearchScene.RADAR_ADAPTIVE)
            .queries(List.of(longQuery))
            .build());
    assertThat(out.getQueries().get(0)).hasSize(UpstreamJsonFields.TAVILY_MAX_QUERY_CHARS);
  }
}
