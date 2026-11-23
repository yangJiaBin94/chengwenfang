package com.chengwenfang.mode.hotnews.application.generation.radarcollect;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** {@link RadarCollectFreshnessGate} 时效硬闸测试。 */
class RadarCollectFreshnessGateTest {

  @Test
  void shouldDiscard_falseWhenFreshnessOff() {
    assertThat(RadarCollectFreshnessGate.shouldDiscard(null, false)).isFalse();
    assertThat(RadarCollectFreshnessGate.shouldDiscard(
            LocalDateTime.now().minusDays(30), false))
        .isFalse();
  }

  @Test
  void shouldDiscard_trueWhenMissingDateAndFreshnessOn() {
    assertThat(RadarCollectFreshnessGate.shouldDiscard(null, true)).isTrue();
    assertThat(RadarCollectFreshnessGate.discardReason(null)).contains("无发布日期");
  }

  @Test
  void shouldDiscard_trueWhenOlderThanSevenDays() {
    LocalDateTime stale = LocalDateTime.now().minusDays(8);
    assertThat(RadarCollectFreshnessGate.shouldDiscard(stale, true)).isTrue();
    assertThat(RadarCollectFreshnessGate.discardReason(stale)).contains("7");
  }

  @Test
  void shouldDiscard_falseWhenWithinSevenDays() {
    LocalDateTime fresh = LocalDateTime.now().minusDays(3);
    assertThat(RadarCollectFreshnessGate.shouldDiscard(fresh, true)).isFalse();
  }
}
