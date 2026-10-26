package com.workbench.mode.hotnews.application.generation.radarcollect;

import static org.assertj.core.api.Assertions.assertThat;

import com.workbench.mode.hotnews.domain.TopicResearchPlanner;
import com.workbench.sourcing.facade.enums.SourceMediaType;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 雷达获取阶段：已选题素材去重。 */
class FetchChannelsSelectedMaterialDedupTest {

  @Test
  void discards_candidate_matching_selected_url() {
    RadarCollectSessionStore.Session session = new RadarCollectSessionStore.Session();
    session.getCandidates().add(candidate(
        "https://example.com/a/b?utm_source=radar&id=1",
        "新抓标题",
        SourceMediaType.ARTICLE.getValue()));
    session.getCandidates().add(candidate(
        "https://other.com/keep",
        "保留标题",
        SourceMediaType.ARTICLE.getValue()));

    Set<String> keys = Set.of(
        TopicResearchPlanner.materialDedupeKey("https://example.com/a/b?id=1", "旧标题"));

    int discarded = FetchChannelsRadarNodeHandler.discardAlreadySelectedMaterials(session, keys);

    assertThat(discarded).isEqualTo(1);
    assertThat(session.survivors()).extracting(RadarCollectCandidate::getTitle)
        .containsExactly("保留标题");
    assertThat(session.getCandidates().get(0).getDiscardReason()).isEqualTo("已选题素材重复");
  }

  @Test
  void discards_title_only_when_url_absent() {
    RadarCollectSessionStore.Session session = new RadarCollectSessionStore.Session();
    session.getCandidates().add(candidate(null, "同一标题", SourceMediaType.ARTICLE.getValue()));
    session.getCandidates().add(candidate(
        "https://keep.com/x",
        "同一标题",
        SourceMediaType.ARTICLE.getValue()));

    Set<String> keys = Set.of(TopicResearchPlanner.materialDedupeKey(null, "同一标题"));

    int discarded = FetchChannelsRadarNodeHandler.discardAlreadySelectedMaterials(session, keys);

    assertThat(discarded).isEqualTo(1);
    assertThat(session.survivors()).extracting(RadarCollectCandidate::getUrl)
        .containsExactly("https://keep.com/x");
  }

  @Test
  void empty_exclusion_set_keeps_all() {
    RadarCollectSessionStore.Session session = new RadarCollectSessionStore.Session();
    session.getCandidates().add(candidate(
        "https://keep.com/a",
        "标题",
        SourceMediaType.ARTICLE.getValue()));

    int discarded = FetchChannelsRadarNodeHandler.discardAlreadySelectedMaterials(session, Set.of());

    assertThat(discarded).isZero();
    assertThat(session.survivors()).hasSize(1);
  }

  private static RadarCollectCandidate candidate(String url, String title, String mediaType) {
    return RadarCollectCandidate.builder()
        .tempId("t-" + title)
        .url(url)
        .title(title)
        .mediaType(mediaType)
        .build();
  }
}
