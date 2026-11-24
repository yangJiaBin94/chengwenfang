package com.chengwenfang.app.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 自动种子只在提示词包和工作流都还没有时触发。
 * <p>沉淀池表由 Flyway 写入基线行，不能用「表为空」判断上次导入是否中断。
 */
class BuiltinCatalogSeedServiceTest {

  @Test
  void emptyLibraryNeedsSeed() {
    assertThat(BuiltinCatalogSeedService.libraryEmpty(0, 0)).isTrue();
  }

  @Test
  void existingPacksOrWorkflowsSkipSeed() {
    assertThat(BuiltinCatalogSeedService.libraryEmpty(12, 0)).isFalse();
    assertThat(BuiltinCatalogSeedService.libraryEmpty(0, 3)).isFalse();
    assertThat(BuiltinCatalogSeedService.libraryEmpty(12, 3)).isFalse();
  }
}
