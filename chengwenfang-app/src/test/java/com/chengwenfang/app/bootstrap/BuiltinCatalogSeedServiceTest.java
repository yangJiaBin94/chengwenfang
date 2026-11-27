package com.chengwenfang.app.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 自动种子触发条件与基线齐备判定。
 * <p>沉淀池表由 Flyway 写入基线行，不能用「表为空」判断上次导入是否中断。
 * 已启用/停用的演示主题视为用户领地，即使内容为空也不再触发自动补种。
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
  void draftThemesWithoutContentStillNeedAutoSeed() {
    assertThat(BuiltinCatalogSeedService.seedBaselineComplete(3, false, 0, false, 5)).isFalse();
    assertThat(BuiltinCatalogSeedService.seedBaselineComplete(3, false, 4, false, 0)).isFalse();
    assertThat(BuiltinCatalogSeedService.seedBaselineComplete(2, false, 4, false, 5)).isFalse();
  }

  @Test
  void confirmedThemesDoNotKeepRetryingEvenWhenEmpty() {
    assertThat(BuiltinCatalogSeedService.seedBaselineComplete(3, true, 0, true, 0)).isTrue();
    assertThat(BuiltinCatalogSeedService.seedBaselineComplete(3, true, 0, false, 5)).isTrue();
    assertThat(BuiltinCatalogSeedService.seedBaselineComplete(3, false, 4, true, 0)).isTrue();
  }

  @Test
  void completeBaselineSkipsAutoSeed() {
    assertThat(BuiltinCatalogSeedService.seedBaselineComplete(3, false, 4, false, 5)).isTrue();
    assertThat(BuiltinCatalogSeedService.seedBaselineComplete(3, true, 4, true, 5)).isTrue();
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
