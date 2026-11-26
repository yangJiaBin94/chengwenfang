package com.chengwenfang.mode.hotnews.application.hotnews;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.content.facade.constant.HotNewsScoringRubricKeys;
import com.chengwenfang.mode.hotnews.domain.TopicDimensionScore;
import com.chengwenfang.mode.hotnews.facade.dto.request.TopicWritingScoreApplyRequest;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsTopicSourceView;
import com.chengwenfang.mode.hotnews.facade.enums.TopicMaterialKind;
import java.util.List;
import org.junit.jupiter.api.Test;

/** lead 硬门槛：打开欲不足 / 单源论文（AI 或域名，按主机去重）。 */
class TopicAiWritingScoreLeadGateTest {

  @Test
  void applyLeadGate_downgradesWhenHookLow() {
    TopicWritingScoreApplyRequest scored = leadRequest(TopicMaterialKind.NEWS, 5);
    TopicWritingScoreApplyRequest gated =
        TopicAiWritingScoreService.applyLeadGate(scored, List.of());
    assertThat(gated.getRecommendUse()).isEqualTo("watch");
    assertThat(gated.getRiskNote()).contains("打开欲不足");
  }

  @Test
  void applyLeadGate_downgradesPaperWhenSourcesEmpty() {
    TopicWritingScoreApplyRequest scored = leadRequest(TopicMaterialKind.PAPER, 9);
    TopicWritingScoreApplyRequest gated =
        TopicAiWritingScoreService.applyLeadGate(scored, List.of());
    assertThat(gated.getRecommendUse()).isEqualTo("watch");
    assertThat(gated.getRiskNote()).contains("单源论文");
  }

  @Test
  void applyLeadGate_downgradesWhenAiMarksPaperSingleSource() {
    TopicWritingScoreApplyRequest scored = leadRequest(TopicMaterialKind.PAPER, 9);
    List<HotNewsTopicSourceView> sources = List.of(HotNewsTopicSourceView.builder()
        .url("https://example.com/some-pdf")
        .build());
    TopicWritingScoreApplyRequest gated =
        TopicAiWritingScoreService.applyLeadGate(scored, sources);
    assertThat(gated.getRecommendUse()).isEqualTo("watch");
    assertThat(gated.getRiskNote()).contains("单源论文");
  }

  @Test
  void applyLeadGate_downgradesDuplicateArxivMirrors() {
    TopicWritingScoreApplyRequest scored = leadRequest(TopicMaterialKind.NEWS, 9);
    List<HotNewsTopicSourceView> sources = List.of(
        HotNewsTopicSourceView.builder().url("https://arxiv.org/abs/2401.00001").build(),
        HotNewsTopicSourceView.builder().url("https://arxiv.org/pdf/2401.00001").build());
    assertThat(TopicAiWritingScoreService.independentSourceCount(sources)).isEqualTo(1);
    TopicWritingScoreApplyRequest gated =
        TopicAiWritingScoreService.applyLeadGate(scored, sources);
    assertThat(gated.getRecommendUse()).isEqualTo("watch");
  }

  @Test
  void applyLeadGate_downgradesHostPreprintEvenIfAiSaysNews() {
    TopicWritingScoreApplyRequest scored = leadRequest(TopicMaterialKind.NEWS, 9);
    List<HotNewsTopicSourceView> sources = List.of(HotNewsTopicSourceView.builder()
        .url("https://www.biorxiv.org/content/10.1101/2024.01.01.123456v1")
        .siteLabel("bioRxiv")
        .build());
    TopicWritingScoreApplyRequest gated =
        TopicAiWritingScoreService.applyLeadGate(scored, sources);
    assertThat(gated.getRecommendUse()).isEqualTo("watch");
  }

  @Test
  void applyLeadGate_keepsLeadWhenHookOkAndMultiHostPaper() {
    TopicWritingScoreApplyRequest scored = leadRequest(TopicMaterialKind.PAPER, 8);
    List<HotNewsTopicSourceView> sources = List.of(
        HotNewsTopicSourceView.builder().url("https://arxiv.org/abs/1").build(),
        HotNewsTopicSourceView.builder().url("https://techcrunch.com/x").build());
    TopicWritingScoreApplyRequest gated =
        TopicAiWritingScoreService.applyLeadGate(scored, sources);
    assertThat(gated.getRecommendUse()).isEqualTo("lead");
  }

  @Test
  void isSingleAcademicPaperSource_detectsOpenReviewHost() {
    assertThat(TopicAiWritingScoreService.isSingleAcademicPaperSource(
            TopicMaterialKind.OTHER.getValue(),
            List.of(HotNewsTopicSourceView.builder()
                .url("https://openreview.net/forum?id=abc")
                .build())))
        .isTrue();
    assertThat(TopicAiWritingScoreService.isSingleAcademicPaperSource(
            TopicMaterialKind.NEWS.getValue(),
            List.of(
                HotNewsTopicSourceView.builder().url("https://example.com/a").build(),
                HotNewsTopicSourceView.builder().url("https://example.com/b").build())))
        .isFalse();
  }

  private static TopicWritingScoreApplyRequest leadRequest(TopicMaterialKind kind, int hook) {
    return TopicWritingScoreApplyRequest.builder()
        .topicId("t1")
        .recommendUse("lead")
        .materialKind(kind.getValue())
        .riskNote("")
        .dimensions(List.of(TopicDimensionScore.builder()
            .key(HotNewsScoringRubricKeys.AUDIENCE_HOOK)
            .score(hook)
            .build()))
        .build();
  }
}
