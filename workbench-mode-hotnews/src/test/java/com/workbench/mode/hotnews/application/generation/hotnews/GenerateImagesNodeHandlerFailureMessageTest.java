package com.workbench.mode.hotnews.application.generation.hotnews;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 配图槽位失败中文文案测试。
 */
class GenerateImagesNodeHandlerFailureMessageTest {

  @Test
  void formatSlotFailureMentionsStepSlotAndTimeoutReason() {
    HotNewsImageSlot slot = new HotNewsImageSlot(3, 4, HotNewsImageKind.INLINE, 2);
    String message = GenerateImagesNodeHandler.formatSlotFailure(
        slot, new RuntimeException("Read timed out"));
    assertThat(message)
        .contains("第 3/4 张")
        .contains("超时")
        .doesNotContain("第 9 步")
        .doesNotContain("Read timed out");
  }
}
