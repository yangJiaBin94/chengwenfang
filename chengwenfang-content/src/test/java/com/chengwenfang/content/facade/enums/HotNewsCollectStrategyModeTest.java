package com.chengwenfang.content.facade.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 采集策略模式枚举单测。 */
class HotNewsCollectStrategyModeTest {

  @Test
  void from_parsesKnownModes() {
    assertEquals(
        HotNewsCollectStrategyMode.STRUCTURED,
        HotNewsCollectStrategyMode.from("structured").orElseThrow());
    assertEquals(
        HotNewsCollectStrategyMode.UNSTRUCTURED,
        HotNewsCollectStrategyMode.from("UNSTRUCTURED").orElseThrow());
  }

  @Test
  void isUnstructured_matches() {
    assertTrue(HotNewsCollectStrategyMode.isUnstructured("unstructured"));
    assertFalse(HotNewsCollectStrategyMode.isUnstructured("structured"));
    assertFalse(HotNewsCollectStrategyMode.isUnstructured(null));
  }
}
