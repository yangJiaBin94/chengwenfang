package com.workbench.ai.facade.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** AI kind 枚举单测。 */
class AiProviderKindTest {

  @Test
  void parsesVision() {
    assertEquals(AiProviderKind.VISION, AiProviderKind.from("vision"));
    assertEquals("vision", AiProviderKind.VISION.getValue());
  }

  @Test
  void rejectsUnknown() {
    assertThrows(IllegalArgumentException.class, () -> AiProviderKind.from("audio"));
  }
}
