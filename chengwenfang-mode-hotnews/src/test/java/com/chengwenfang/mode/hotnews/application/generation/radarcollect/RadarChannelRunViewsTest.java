package com.chengwenfang.mode.hotnews.application.generation.radarcollect;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.sourcing.facade.dto.view.HotNewsChannelRunView;
import com.chengwenfang.sourcing.facade.enums.ChannelRunStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 分渠道抓取结果映射单测。 */
class RadarChannelRunViewsTest {

  @Test
  void mapsSuccessAndFailureForCoverage() {
    LocalDateTime at = LocalDateTime.of(2026, 10, 27, 12, 0);
    HotNewsChannelRunView ok = RadarChannelRunViews.fromFetchOutcome(
        "ch-ok", "SearXNG", "searxng", true, null, 12, 5, at);
    HotNewsChannelRunView failed = RadarChannelRunViews.fromFetchOutcome(
        "ch-fail", "GitHub", "github", false, "upstream_timeout", 0, 0, at);

    List<HotNewsChannelRunView> runs = List.of(ok, failed);
    assertThat(runs).hasSize(2);

    assertThat(ok.getChannelId()).isEqualTo("ch-ok");
    assertThat(ok.getStatus()).isEqualTo(ChannelRunStatus.SUCCEEDED.getValue());
    assertThat(ok.getItemsCount()).isEqualTo(12);
    assertThat(ok.getNewItems()).isEqualTo(5);
    assertThat(ok.getErrorCode()).isNull();

    assertThat(failed.getStatus()).isEqualTo(ChannelRunStatus.FAILED.getValue());
    assertThat(failed.getErrorCode()).isEqualTo("upstream_timeout");
    assertThat(failed.getErrorMessage()).isEqualTo("upstream_timeout");

    long succeeded = runs.stream()
        .filter(run -> ChannelRunStatus.SUCCEEDED.getValue().equals(run.getStatus()))
        .count();
    int coverage = (int) Math.round(succeeded * 100.0 / runs.size());
    assertThat(coverage).isEqualTo(50);
  }
}
