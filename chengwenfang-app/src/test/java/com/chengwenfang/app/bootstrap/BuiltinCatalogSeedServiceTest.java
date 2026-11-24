package com.chengwenfang.app.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 自动种子触发条件与基线齐备判定。
 * <p>沉淀池表由 Flyway 写入基线行，不能用「表为空」判断上次导入是否中断。
 */
class BuiltinCatalogSeedServiceTest {

  @Test
  void emptyLibraryNeedsSeed() {
    assertThat(BuiltinCatalogSeedService.libraryEmpty(0, 0)).isTrue();
  }

  @Test
  void existingPacksOrWorkflowsNotEmptyLibrary() {
    assertThat(BuiltinCatalogSeedService.libraryEmpty(12, 0)).isFalse();
    assertThat(BuiltinCatalogSeedService.libraryEmpty(0, 3)).isFalse();
    assertThat(BuiltinCatalogSeedService.libraryEmpty(12, 3)).isFalse();
  }

  @Test
  void incompleteThemesStillNeedAutoSeed() {
    assertThat(BuiltinCatalogSeedService.seedBaselineComplete(3, 0, 5)).isFalse();
    assertThat(BuiltinCatalogSeedService.seedBaselineComplete(3, 4, 0)).isFalse();
    assertThat(BuiltinCatalogSeedService.seedBaselineComplete(2, 4, 5)).isFalse();
  }

  @Test
  void completeBaselineSkipsAutoSeed() {
    assertThat(BuiltinCatalogSeedService.seedBaselineComplete(3, 4, 5)).isTrue();
  }

  @Test
  void ensureSeedThemeStubMustNotBlockCourseBriefingSeed() {
    String stub = """
        {"schema_version":1,"step":1,"naming":{"name":"AI 自学零基础","code":"ai_self_study_zero"}}
        """;
    assertThat(BuiltinCatalogSeedService.shouldPreserveCourseBriefing(stub)).isFalse();
    assertThat(BuiltinCatalogSeedService.shouldPreserveCourseBriefing(null)).isFalse();

    String progressed = """
        {"schema_version":1,"step":3,"goals":{"outcomes":["会用"]},"naming":{"name":"用户改过"}}
        """;
    assertThat(BuiltinCatalogSeedService.shouldPreserveCourseBriefing(progressed)).isTrue();
  }
}
