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
    assertThat(slots.get(0).slotIndex()).isEqualTo(1);
    assertThat(slots.get(0).totalSlots()).isEqualTo(4);
    assertThat(slots.get(0).kind()).isEqualTo(HotNewsImageKind.COVER);
    assertThat(slots.get(0).sequenceNo()).isEqualTo(0);
    assertThat(slots.get(1).kind()).isEqualTo(HotNewsImageKind.INLINE);
    assertThat(slots.get(1).sequenceNo()).isEqualTo(1);
    assertThat(slots.get(2).sequenceNo()).isEqualTo(2);
    assertThat(slots.get(3).slotIndex()).isEqualTo(4);
    assertThat(slots.get(3).sequenceNo()).isEqualTo(3);
  }
}
