package com.workbench.mode.hotnews.application.generation.hotnews;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 热点配图槽位标识测试。
 */
class HotNewsImageSlotTest {

  @Test
  void plansCoverAsSlotOneThenInlineSlots() {
    List<HotNewsImageSlot> slots = HotNewsImageSlot.plan(3);
    assertThat(slots).hasSize(4);
    assertThat(slots.get(0)).isEqualTo(
        new HotNewsImageSlot(1, 4, HotNewsImageKind.COVER, 0));
    assertThat(slots.get(1)).isEqualTo(
        new HotNewsImageSlot(2, 4, HotNewsImageKind.INLINE, 1));
    assertThat(slots.get(2)).isEqualTo(
        new HotNewsImageSlot(3, 4, HotNewsImageKind.INLINE, 2));
    assertThat(slots.get(3)).isEqualTo(
        new HotNewsImageSlot(4, 4, HotNewsImageKind.INLINE, 3));
  }
}
