package com.workbench.app.golden.fingerprint;

import static org.assertj.core.api.Assertions.assertThat;

import cn.hutool.crypto.digest.DigestUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * 内置 Skill/工作流目录指纹：与黄金基线 {@code catalog.sha256} 比对。
 */
class BuiltinCatalogFingerprintTest {
  @Test
  void builtinCatalog_sha256_matchesBaseline() throws Exception {
    Path catalog = GoldenRepoPaths.builtinCatalog();
    Path expectedFile = GoldenRepoPaths.baselineDir().resolve("catalog.sha256");
    assertThat(catalog).exists();
    assertThat(expectedFile).exists();

    String actual = DigestUtil.sha256Hex(Files.readAllBytes(catalog));
    String expected = Files.readString(expectedFile).trim();
    assertThat(actual)
        .as("builtin-catalog.json 与 golden/baseline/catalog.sha256 不一致；"
            + "若有意更新种子请重算 SHA-256 并写入 catalog.sha256")
        .isEqualTo(expected);
  }
}
