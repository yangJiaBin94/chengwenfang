package com.chengwenfang.content.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.content.domain.entity.ContentThemeEntity;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * 已软删主题闸门：deleted_at 非空时拒绝展示。
 */
class ThemeVisibilityGuardTest {
  private final ThemeVisibilityGuard guard =
      new ThemeVisibilityGuard(null, null, null, null, null);

  @Test
  void assertThemeActive_rejectsDeletedTheme() {
    ContentThemeEntity theme = ContentThemeEntity.builder()
        .id("theme-1")
        .deletedAt(LocalDateTime.now())
        .build();
    BusinessException exception = assertThrows(BusinessException.class, () -> guard.assertThemeActive(theme));
    assertEquals(ErrorCodes.THEME_DELETED, exception.getCode());
  }

  @Test
  void assertThemeActive_allowsActiveTheme() {
    ContentThemeEntity theme = ContentThemeEntity.builder().id("theme-1").build();
    assertDoesNotThrow(() -> guard.assertThemeActive(theme));
    assertDoesNotThrow(() -> guard.assertThemeActive(null));
  }
}
