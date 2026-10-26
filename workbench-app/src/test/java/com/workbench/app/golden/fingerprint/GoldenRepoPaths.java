package com.workbench.app.golden.fingerprint;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 从当前工作目录向上定位仓库根与黄金基线路径。
 */
public final class GoldenRepoPaths {
  private GoldenRepoPaths() {}

  /**
   * 定位含 {@code scripts/bootstrap/builtin-catalog.json} 的仓库根。
   *
   * @return 仓库根路径
   */
  public static Path repoRoot() {
    Path cursor = Path.of("").toAbsolutePath().normalize();
    for (int depth = 0; depth < 8; depth++) {
      Path catalog = cursor.resolve("scripts/bootstrap/builtin-catalog.json");
      if (Files.isRegularFile(catalog)) {
        return cursor;
      }
      Path parent = cursor.getParent();
      if (parent == null) {
        break;
      }
      cursor = parent;
    }
    throw new IllegalStateException("未找到 scripts/bootstrap/builtin-catalog.json，cwd="
        + Path.of("").toAbsolutePath());
  }

  /**
   * @return 内置目录文件
   */
  public static Path builtinCatalog() {
    return repoRoot().resolve("scripts/bootstrap/builtin-catalog.json");
  }

  /**
   * @return 黄金基线目录
   */
  public static Path baselineDir() {
    return repoRoot().resolve("workbench-app/src/test/resources/golden/baseline");
  }
}
