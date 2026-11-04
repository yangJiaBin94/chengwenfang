package com.chengwenfang.crawler.application.article;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.chengwenfang.crawler.facade.dto.view.PageScreenshotResult;
import com.chengwenfang.infra.config.ChengwenfangProperties;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 调用本机 page-screenshot CLI（Playwright 分段截图）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PageScreenshotClient {

  /** Python 模块名。 */
  private static final String MODULE_NAME = "page_screenshot";

  /** 成文坊配置。 */
  private final ChengwenfangProperties properties;

  /**
   * 是否启用截图 CLI。
   *
   * @return 启用时 true
   */
  public boolean isEnabled() {
    ChengwenfangProperties.PageScreenshotSettings settings = properties.getPageScreenshot();
    return settings != null && settings.isEnabled();
  }

  /**
   * 对 URL 分段截图。
   *
   * @param url 页面地址
   * @return 结果；未启用或失败时 ok=false
   */
  public PageScreenshotResult capture(String url) {
    ChengwenfangProperties.PageScreenshotSettings settings = properties.getPageScreenshot();
    if (settings == null || !settings.isEnabled() || StrUtil.isBlank(url)) {
      return PageScreenshotResult.builder().ok(false).error("page-screenshot 未启用").build();
    }
    Path cwd = resolveWorkingDirectory(settings.getWorkingDirectory());
    Path python = resolveExecutable(settings.getPythonExecutable());
    int timeout = settings.getTimeoutSeconds() <= 0 ? 45 : settings.getTimeoutSeconds();
    int maxShots = settings.getMaxShots() <= 0 ? 3 : settings.getMaxShots();
    String viewport = StrUtil.blankToDefault(settings.getViewport(), "1280x900");

    Path outDir;
    try {
      outDir = Files.createTempDirectory("cwf-page-shot-" + UUID.randomUUID());
    } catch (Exception exception) {
      return PageScreenshotResult.builder()
          .ok(false)
          .error("无法创建临时目录: " + exception.getMessage())
          .build();
    }

    List<String> command = new ArrayList<>();
    command.add(python.toString());
    command.add("-m");
    command.add(MODULE_NAME);
    command.add("--url");
    command.add(url.trim());
    command.add("--out-dir");
    command.add(outDir.toAbsolutePath().toString());
    command.add("--max-shots");
    command.add(String.valueOf(maxShots));
    command.add("--viewport");
    command.add(viewport);
    command.add("--timeout-seconds");
    command.add(String.valueOf(timeout));

    ProcessBuilder builder = new ProcessBuilder(command);
    builder.directory(cwd.toFile());
    builder.redirectErrorStream(true);
    builder.environment().put("PYTHONIOENCODING", "utf-8");
    builder.environment().put("PYTHONUTF8", "1");
    Process process = null;
    boolean keepOutDir = false;
    try {
      process = builder.start();
      Process running = process;
      CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> readAll(running));
      int waitSeconds = timeout + 30;
      boolean finished = process.waitFor(waitSeconds, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        return PageScreenshotResult.builder()
            .ok(false)
            .error("page-screenshot CLI 超时（" + waitSeconds + "s）")
            .build();
      }
      String output = outputFuture.get(Math.min(5, waitSeconds), TimeUnit.SECONDS);
      String jsonLine = lastJsonLine(output);
      if (StrUtil.isBlank(jsonLine)) {
        return PageScreenshotResult.builder()
            .ok(false)
            .error("page-screenshot 无 JSON 输出: " + StrUtil.maxLength(StrUtil.nullToEmpty(output), 400))
            .build();
      }
      PageScreenshotResult parsed = JSONUtil.toBean(jsonLine, PageScreenshotResult.class);
      if (parsed == null) {
        return PageScreenshotResult.builder().ok(false).error("page-screenshot JSON 解析失败").build();
      }
      if (parsed.getPaths() == null) {
        parsed.setPaths(List.of());
      }
      // 成功路径由调用方 cleanup；失败则本方法收临时目录
      keepOutDir = parsed.isOk() && CollUtil.isNotEmpty(parsed.getPaths());
      return parsed;
    } catch (Exception exception) {
      if (process != null) {
        process.destroyForcibly();
      }
      log.info("page-screenshot CLI 失败 url={} err={}", url, exception.getMessage());
      return PageScreenshotResult.builder().ok(false).error(exception.getMessage()).build();
    } finally {
      if (!keepOutDir) {
        deleteDirectoryQuietly(outDir);
      }
    }
  }

  /**
   * 静默删除临时截图目录。
   *
   * @param directory 目录
   */
  private static void deleteDirectoryQuietly(Path directory) {
    if (directory == null) {
      return;
    }
    try {
      FileUtil.del(directory.toFile());
    } catch (Exception ignored) {
      // 清理失败不阻断
    }
  }

  /**
   * 删除截图文件及空父目录。
   *
   * @param paths PNG 路径
   */
  public void cleanup(List<String> paths) {
    if (paths == null || paths.isEmpty()) {
      return;
    }
    Path parent = null;
    for (String pathText : paths) {
      if (StrUtil.isBlank(pathText)) {
        continue;
      }
      try {
        Path path = Path.of(pathText);
        if (parent == null && path.getParent() != null) {
          parent = path.getParent();
        }
        Files.deleteIfExists(path);
      } catch (Exception ignored) {
        // 清理失败不阻断
      }
    }
    if (parent != null) {
      try {
        Files.deleteIfExists(parent);
      } catch (Exception ignored) {
        // 目录非空则忽略
      }
    }
  }

  private static String readAll(Process process) {
    StringBuilder output = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        output.append(line).append('\n');
      }
    } catch (Exception ignored) {
      return output.toString();
    }
    return output.toString();
  }

  private static String lastJsonLine(String output) {
    if (StrUtil.isBlank(output)) {
      return "";
    }
    String[] lines = output.split("\n");
    for (int index = lines.length - 1; index >= 0; index--) {
      String line = StrUtil.trim(lines[index]);
      if (line.startsWith("{") && line.endsWith("}")) {
        return line;
      }
    }
    return "";
  }

  private Path resolveExecutable(String configured) {
    Path path = Path.of(StrUtil.blankToDefault(configured, "python"));
    if (path.isAbsolute()) {
      return path.normalize();
    }
    Path fromContentRoot =
        Path.of(properties.getContentRoot()).resolve(path).toAbsolutePath().normalize();
    if (Files.isRegularFile(fromContentRoot) || Files.isExecutable(fromContentRoot)) {
      return fromContentRoot;
    }
    Path fromCwd = Path.of(".").resolve(path).toAbsolutePath().normalize();
    if (Files.isRegularFile(fromCwd)) {
      return fromCwd;
    }
    return path;
  }

  private Path resolveWorkingDirectory(String configured) {
    Path path = Path.of(StrUtil.blankToDefault(configured, "scripts/page-screenshot"));
    if (path.isAbsolute()) {
      return path.normalize();
    }
    Path fromContentRoot =
        Path.of(properties.getContentRoot()).resolve(path).toAbsolutePath().normalize();
    if (Files.isDirectory(fromContentRoot)) {
      return fromContentRoot;
    }
    return Path.of(".").resolve(path).toAbsolutePath().normalize();
  }
}
