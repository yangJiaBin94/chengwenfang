package com.chengwenfang.mode.hotnews.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.crawler.facade.dto.view.SearchHitView;
import com.chengwenfang.mode.hotnews.application.research.ResearchAiWebSearchCollectResult;
import com.chengwenfang.mode.hotnews.application.research.ResearchSourcePolicy;
import com.chengwenfang.mode.hotnews.domain.TopicResearchPlanner;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsResearchAgentLimits;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** {@link ResearchSupplementAgent} 播种合并与配额。 */
class ResearchSupplementAgentSeedMergeTest {

  @Test
  void merge_reserves_slots_for_ai_when_channel_is_full() {
    Map<String, SearchHitView> channelHits = fullChannelHits(HotNewsResearchAgentLimits.MAX_HITS);
    List<SearchHitView> aiHits = new ArrayList<>();
    for (int index = 1; index <= 5; index++) {
      aiHits.add(new SearchHitView(
          "AI" + index, "https://ai.example/" + index, "ai-snippet"));
    }
    Map<String, SearchHitView> hitByUrl = new LinkedHashMap<>();

    ResearchSupplementAgent.mergeParallelSeedHits(
        channelHits, aiHits, Set.of(), ResearchSourcePolicy.builder().build(), hitByUrl);

    assertThat(hitByUrl).hasSize(HotNewsResearchAgentLimits.MAX_HITS);
    assertThat(countHost(hitByUrl, "ai.example")).isEqualTo(5);
    assertThat(countHost(hitByUrl, "channel.example"))
        .isEqualTo(HotNewsResearchAgentLimits.MAX_HITS - 5);
  }

  @Test
  void merge_gives_channel_full_pool_when_ai_empty() {
    Map<String, SearchHitView> hitByUrl = new LinkedHashMap<>();
    ResearchSupplementAgent.mergeParallelSeedHits(
        fullChannelHits(HotNewsResearchAgentLimits.MAX_HITS),
        List.of(),
        Set.of(),
        null,
        hitByUrl);

    assertThat(hitByUrl).hasSize(HotNewsResearchAgentLimits.MAX_HITS);
    assertThat(hitByUrl.keySet()).allMatch(key -> key.contains("channel.example"));
  }

  @Test
  void merge_does_not_over_reserve_when_ai_hits_all_filtered() {
    Map<String, SearchHitView> hitByUrl = new LinkedHashMap<>();
    List<SearchHitView> aiHits = List.of(
        new SearchHitView("坏1", "https://a.blocked.example/1", ""),
        new SearchHitView("坏2", "https://b.blocked.example/2", ""));

    ResearchSupplementAgent.mergeParallelSeedHits(
        fullChannelHits(HotNewsResearchAgentLimits.MAX_HITS),
        aiHits,
        Set.of(),
        ResearchSourcePolicy.builder().blockedDomains(List.of("blocked.example")).build(),
        hitByUrl);

    assertThat(hitByUrl).hasSize(HotNewsResearchAgentLimits.MAX_HITS);
    assertThat(hitByUrl.keySet()).allMatch(key -> key.contains("channel.example"));
  }

  @Test
  void merge_backfills_channel_when_ai_overlaps_channel_urls() {
    Map<String, SearchHitView> channelHits = fullChannelHits(HotNewsResearchAgentLimits.MAX_HITS);
    // AI 全部与渠道前 5 条撞车 → 预留 5 却入池 0，应回填渠道占满
    List<SearchHitView> aiHits = new ArrayList<>();
    for (int index = 1; index <= 5; index++) {
      aiHits.add(new SearchHitView(
          "AI撞车" + index, "https://channel.example/" + index, "overlap"));
    }
    Map<String, SearchHitView> hitByUrl = new LinkedHashMap<>();

    ResearchSupplementAgent.mergeParallelSeedHits(
        channelHits, aiHits, Set.of(), ResearchSourcePolicy.builder().build(), hitByUrl);

    assertThat(hitByUrl).hasSize(HotNewsResearchAgentLimits.MAX_HITS);
    assertThat(countHost(hitByUrl, "channel.example"))
        .isEqualTo(HotNewsResearchAgentLimits.MAX_HITS);
  }

  @Test
  void apply_seed_keeps_channel_hits_when_ai_future_degraded() {
    Map<String, SearchHitView> channelHits = fullChannelHits(3);
    Map<String, SearchHitView> hitByUrl = new LinkedHashMap<>();
    List<String> queriesRun = new ArrayList<>();
    StringBuilder toolLog = new StringBuilder();

    ResearchSupplementAgent.applyParallelSeedResults(
        new ResearchSupplementAgent.ChannelSeedResult(
            channelHits, List.of("苹果 财报"), "channel ok\n"),
        ResearchAiWebSearchCollectResult.builder()
            .used(false)
            .skipReason("AI 联网 Future 失败：boom")
            .build(),
        Set.of(),
        ResearchSourcePolicy.builder().build(),
        hitByUrl,
        queriesRun,
        toolLog);

    assertThat(hitByUrl).hasSize(3);
    assertThat(queriesRun).containsExactly("苹果 财报");
    assertThat(toolLog.toString()).contains("channel ok");
    assertThat(toolLog.toString()).contains("ai_web_search skipped=");
  }

  @Test
  void mergeHits_filters_blocked_domain() {
    Map<String, SearchHitView> source = new LinkedHashMap<>();
    String badUrl = "https://spam.blocked.example/a";
    String goodUrl = "https://news.example/ok";
    source.put(TopicResearchPlanner.normalizeUrl(badUrl), new SearchHitView("坏", badUrl, ""));
    source.put(TopicResearchPlanner.normalizeUrl(goodUrl), new SearchHitView("好", goodUrl, ""));
    Map<String, SearchHitView> hitByUrl = new LinkedHashMap<>();

    int added = ResearchSupplementAgent.mergeHits(
        source,
        Set.of(),
        ResearchSourcePolicy.builder().blockedDomains(List.of("blocked.example")).build(),
        hitByUrl,
        HotNewsResearchAgentLimits.MAX_HITS);

    assertThat(added).isEqualTo(1);
    assertThat(hitByUrl).hasSize(1);
    assertThat(hitByUrl.values().iterator().next().getUrl()).isEqualTo(goodUrl);
  }

  private static Map<String, SearchHitView> fullChannelHits(int count) {
    Map<String, SearchHitView> channelHits = new LinkedHashMap<>();
    for (int index = 1; index <= count; index++) {
      String url = "https://channel.example/" + index;
      channelHits.put(TopicResearchPlanner.normalizeUrl(url), new SearchHitView(
          "渠道" + index, url, "snippet"));
    }
    return channelHits;
  }

  private static long countHost(Map<String, SearchHitView> hitByUrl, String hostNeedle) {
    return hitByUrl.keySet().stream().filter(key -> key.contains(hostNeedle)).count();
  }
}
