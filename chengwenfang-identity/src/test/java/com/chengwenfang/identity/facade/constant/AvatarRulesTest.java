package com.chengwenfang.identity.facade.constant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** {@link AvatarRules} 魔数识别自检。 */
class AvatarRulesTest {

  @Test
  void detectMime_recognizesJpegPngWebp() {
    byte[] jpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, 0x00, 0x10};
    byte[] png =
        new byte[] {
          (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x00
        };
    byte[] webp =
        new byte[] {
          'R', 'I', 'F', 'F', 0x00, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P'
        };
    assertEquals("image/jpeg", AvatarRules.detectMime(jpeg));
    assertEquals("image/png", AvatarRules.detectMime(png));
    assertEquals("image/webp", AvatarRules.detectMime(webp));
    assertNull(AvatarRules.detectMime(new byte[] {0x00, 0x01, 0x02}));
  }

  @Test
  void matchesClaimed_acceptsBlankOrSameMime() {
    assertTrue(AvatarRules.matchesClaimed(null, "image/png"));
    assertTrue(AvatarRules.matchesClaimed("image/png; charset=binary", "image/png"));
    assertTrue(AvatarRules.matchesClaimed("image/jpg", "image/jpeg"));
    assertFalse(AvatarRules.matchesClaimed("image/jpeg", "image/png"));
  }
}
