package com.chengwenfang.mode.hotnews.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 视频时长秒数取整与空值处理。 */
class VideoDurationSecondsTest {

  @Test
  void to_rounded_duration_seconds_rounds_positive_values() {
    assertThat(VideoTranscriptService.toRoundedDurationSeconds(65.4)).isEqualTo(65);
    assertThat(VideoTranscriptService.toRoundedDurationSeconds(65.6)).isEqualTo(66);
    assertThat(VideoTranscriptService.toRoundedDurationSeconds(1.0)).isEqualTo(1);
  }

  @Test
  void to_rounded_duration_seconds_returns_null_for_invalid() {
    assertThat(VideoTranscriptService.toRoundedDurationSeconds(null)).isNull();
    assertThat(VideoTranscriptService.toRoundedDurationSeconds(0.0)).isNull();
    assertThat(VideoTranscriptService.toRoundedDurationSeconds(-3.0)).isNull();
  }
}
