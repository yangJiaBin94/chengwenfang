package com.chengwenfang.app.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 自动种子在主题确认中断后仍应补上信源沉淀池。
 */
class BuiltinCatalogSeedServiceTest {

  @Test
  void emptyLibraryNeedsSeed() {
    assertThat(BuiltinCatalogSeedService.needsAutoSeed(0, 0, 0)).isTrue();
  }

  @Test
  void packsWithoutSourceChannelsStillNeedSeed() {
    assertThat(BuiltinCatalogSeedService.needsAutoSeed(12, 3, 0)).isTrue();
    assertThat(BuiltinCatalogSeedService.libraryEmpty(12, 3)).isFalse();
  }

  @Test
  void populatedLibrarySkipsSeed() {
    assertThat(BuiltinCatalogSeedService.needsAutoSeed(12, 3, 4)).isFalse();
  }
}
