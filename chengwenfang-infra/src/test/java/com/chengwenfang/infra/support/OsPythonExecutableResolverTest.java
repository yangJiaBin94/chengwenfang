package com.chengwenfang.infra.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 操作系统感知的 Python 路径：非 Windows 不必手改 {@code python-executable}。
 */
class OsPythonExecutableResolverTest {

  @TempDir
  Path root;

  @Test
  void unixIgnoresWindowsVenvPathAndUsesBinPython() throws Exception {
    Path unixPython = root.resolve("scripts/article-extract/.venv/bin/python");
    Files.createDirectories(unixPython.getParent());
    Files.writeString(unixPython, "#!/bin/sh\n");

    Path resolved = OsPythonExecutableResolver.resolve(
        root,
        "scripts/article-extract",
        "scripts/article-extract/.venv/Scripts/python.exe",
        false);

    assertThat(resolved).isEqualTo(unixPython.toAbsolutePath().normalize());
  }

  @Test
  void windowsUsesScriptsPythonEvenWhenConfigIsGeneric() throws Exception {
    Path windowsPython = root.resolve("scripts/video-transcript/.venv/Scripts/python.exe");
    Files.createDirectories(windowsPython.getParent());
    Files.writeString(windowsPython, "");

    Path resolved = OsPythonExecutableResolver.resolve(
        root,
        "scripts/video-transcript",
        "python",
        true);

    assertThat(resolved).isEqualTo(windowsPython.toAbsolutePath().normalize());
  }

  @Test
  void explicitExistingOverrideWins() throws Exception {
    Path custom = root.resolve("tools/python");
    Files.createDirectories(custom.getParent());
    Files.writeString(custom, "");

    Path resolved = OsPythonExecutableResolver.resolve(
        root,
        "scripts/page-screenshot",
        custom.toString(),
        false);

    assertThat(resolved).isEqualTo(custom.toAbsolutePath().normalize());
  }

  @Test
  void missingVenvFallsBackToPathCommand() {
    Path resolved = OsPythonExecutableResolver.resolve(
        root,
        "scripts/article-extract",
        "python",
        true);

    assertThat(resolved).isEqualTo(Path.of("python"));
  }

  @Test
  void foreignWindowsPathWithoutVenvFallsBackToUnixCommand() {
    Path resolved = OsPythonExecutableResolver.resolve(
        root,
        "scripts/article-extract",
        "scripts/article-extract/.venv/Scripts/python.exe",
        false);

    assertThat(resolved).isEqualTo(Path.of("python3"));
  }
}
