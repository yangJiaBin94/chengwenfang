package com.chengwenfang.app.golden;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 黄金基线相关路径解析。
 */
public final class GoldenPaths {
  private GoldenPaths() {}

  /**
   * 源码树中 baseline 根目录：{@code src/test/resources/golden/baseline}。
   *
   * @return 绝对路径
   */
  public static Path baselineRoot() {
    Path override = pathFromProperty("golden.baseline.dir");
    if (override != null) {
      return override;
    }
    return Paths.get("src", "test", "resources", "golden", "baseline").toAbsolutePath().normalize();
  }

  /**
   * 某场景的 baseline 目录。
   *
   * @param scenarioId 场景 ID
   * @return 目录
   */
  public static Path baselineDir(String scenarioId) {
    return baselineRoot().resolve(scenarioId);
  }

  /**
   * 比对失败时差异输出根目录：{@code target/golden-diff}。
   *
   * @return 绝对路径
   */
  public static Path diffRoot() {
    Path override = pathFromProperty("golden.diff.dir");
    if (override != null) {
      return override;
    }
    return Paths.get("target", "golden-diff").toAbsolutePath().normalize();
  }

  private static Path pathFromProperty(String key) {
    String raw = System.getProperty(key);
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return Paths.get(raw).toAbsolutePath().normalize();
  }
}
