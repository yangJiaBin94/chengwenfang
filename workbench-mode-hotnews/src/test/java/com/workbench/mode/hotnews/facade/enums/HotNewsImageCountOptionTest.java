package com.workbench.mode.hotnews.facade.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HotNewsImageCountOptionTest {
  @Test
  void normalize_legacy_zero_and_one_two_to_minimum() {
    assertThat(HotNewsImageCountOption.normalize("0")).isEqualTo("2-3");
    assertThat(HotNewsImageCountOption.normalize("1-2")).isEqualTo("2-3");
    assertThat(HotNewsImageCountOption.resolveInlineCount("0")).isEqualTo(2);
  }

  @Test
  void resolve_known_buckets() {
    assertThat(HotNewsImageCountOption.resolveInlineCount("2-3")).isEqualTo(2);
    assertThat(HotNewsImageCountOption.resolveInlineCount("3-5")).isEqualTo(3);
    assertThat(HotNewsImageCountOption.resolveInlineCount("6-8")).isEqualTo(6);
  }
}
