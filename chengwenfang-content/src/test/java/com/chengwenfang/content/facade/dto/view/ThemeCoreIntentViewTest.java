package com.chengwenfang.content.facade.dto.view;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 主要意图视图单测。 */
class ThemeCoreIntentViewTest {

  @Test
  void toPromptBlock_includesWantsAndAvoids() {
    ThemeCoreIntentView intent = ThemeCoreIntentView.builder()
        .summary("盯 A 股可写事件")
        .materialWants(List.of("板块异动", "IPO"))
        .materialAvoids(List.of("机构百科"))
        .successLooksLike("近几天可核实事件")
        .confirmed(true)
        .build();
    String block = intent.toPromptBlock();
    assertTrue(block.contains("盯 A 股可写事件"));
    assertTrue(block.contains("板块异动"));
    assertTrue(block.contains("机构百科"));
    assertTrue(block.contains("近几天可核实事件"));
  }

  @Test
  void toPromptBlock_blankWithoutSummary() {
    assertFalse(ThemeCoreIntentView.builder().build().hasSummary());
    assertTrue(ThemeCoreIntentView.builder().build().toPromptBlock().isEmpty());
  }
}
