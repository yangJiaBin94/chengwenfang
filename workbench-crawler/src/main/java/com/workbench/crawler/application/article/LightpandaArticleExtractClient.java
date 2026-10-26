package com.workbench.crawler.application.article;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.workbench.crawler.facade.constant.ArticleBrowserModes;
import com.workbench.crawler.facade.constant.ArticleContentDefaults;
import com.workbench.infra.config.WorkbenchProperties;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 调用本机 article_extract CLI 的 Lightpanda 路径：渲染 HTML 后再抽取正文。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LightpandaArticleExtractClient implements LightpandaArticleExtractor {
  /** Python 模块名。 */
  private static final String MODULE_NAME = "article_extract";

  /** 工作台配置。 */
  private final WorkbenchProperties properties;

  /**
   * 经 Lightpanda 渲染后抽取正文。
   *
   * @param url 文章地址
   * @return CLI 结果；未启用或失败时 ok=false
   */
  @Override
  public TrafilaturaCliResult extract(String url) {
    WorkbenchProperties.ArticleContentSettings contentSettings = properties.getArticleContent();
    WorkbenchProperties.ArticleExtractSettings extractSettings = properties.getArticleExtract();
    if (contentSettings == null
        || !contentSettings.isBrowserEnabled()
        || extractSettings == null
        || !extractSettings.isEnabled()
        || StrUtil.isBlank(url)) {
      return TrafilaturaCliResult.builder().ok(false).error("Lightpanda 未启用").build();
    }
    Path cwd = resolveWorkingDirectory(extractSettings.getWorkingDirectory());
    Path python = resolveExecutable(extractSettings.getPythonExecutable());
    String mode = ArticleBrowserModes.normalize(contentSettings.getBrowserMode());
    String container = StrUtil.blankToDefault(
        contentSettings.getBrowserDockerContainer(),
        ArticleContentDefaults.BROWSER_DEFAULT_DOCKER_CONTAINER);
    String binary = StrUtil.blankToDefault(
        contentSettings.getBrowserExecutable(),
        ArticleContentDefaults.BROWSER_DEFAULT_EXECUTABLE);
    int timeout = contentSettings.getBrowserTimeoutSeconds() <= 0
        ? ArticleContentDefaults.BROWSER_DEFAULT_TIMEOUT_SECONDS
        : contentSettings.getBrowserTimeoutSeconds();

    List<String> command = new ArrayList<>();
    command.add(python.toString());
    command.add("-m");
    command.add(MODULE_NAME);
    command.add("--url");
    command.add(url.trim());
    command.add("--via");
    command.add("lightpanda");
    command.add("--browser-mode");
    command.add(mode);
    command.add("--docker-container");
    command.add(container);
    command.add("--lightpanda-bin");
    command.add(binary);
    command.add("--timeout-seconds");
    command.add(String.valueOf(timeout));

    ProcessBuilder builder = new ProcessBuilder(command);
    builder.directory(cwd.toFile());
    builder.redirectErrorStream(true);
    builder.environment().put("PYTHONIOENCODING", "utf-8");
    builder.environment().put("PYTHONUTF8", "1");
    Process process = null;
    try {
      process = builder.start();
      Process running = process;
      // 并行排空 stdout，避免先读阻塞导致 waitFor 超时失效
      CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> readAll(running));
      // CLI 内还有 lightpanda 超时；外层多留缓冲
      int waitSeconds = timeout + 15;
      boolean finished = process.waitFor(waitSeconds, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        return TrafilaturaCliResult.builder()
            .ok(false)
            .error("Lightpanda CLI 超时（" + waitSeconds + "s）")
            .build();
      }
      String output = outputFuture.get(Math.min(5, waitSeconds), TimeUnit.SECONDS);
      String jsonLine = lastJsonLine(output);
      if (StrUtil.isBlank(jsonLine)) {
        return TrafilaturaCliResult.builder()
            .ok(false)
            .error("Lightpanda CLI 无 JSON 输出: " + StrUtil.maxLength(StrUtil.nullToEmpty(output), 400))
            .build();
      }
      TrafilaturaCliResult parsed = JSONUtil.toBean(jsonLine, TrafilaturaCliResult.class);
      if (parsed == null) {
        return TrafilaturaCliResult.builder().ok(false).error("Lightpanda CLI JSON 解析失败").build();
      }
      return parsed;
    } catch (Exception exception) {
      if (process != null) {
        process.destroyForcibly();
      }
      log.info("Lightpanda CLI 调用失败 url={} err={}", url, exception.getMessage());
      return TrafilaturaCliResult.builder().ok(false).error(exception.getMessage()).build();
    }
  }

  /**
   * 读取进程全部标准输出。
   *
   * @param process 进程
   * @return 输出文本
   */
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

  /**
   * 解析 Python 可执行路径（相对 contentRoot）。
   *
   * @param configured 配置值
   * @return 路径
   */
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

  /**
   * 解析 CLI 工作目录。
   *
   * @param configured 配置值
   * @return 目录
   */
  private Path resolveWorkingDirectory(String configured) {
    Path path = Path.of(StrUtil.blankToDefault(configured, "scripts/article-extract"));
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

  /**
   * 取 stdout 中最后一行 JSON。
   *
   * @param output 进程输出
   * @return JSON 行或空
   */
  private static String lastJsonLine(String output) {
    if (StrUtil.isBlank(output)) {
      return "";
    }
    String[] lines = output.split("\\R");
    for (int index = lines.length - 1; index >= 0; index--) {
      String line = StrUtil.trim(lines[index]);
      if (line.startsWith("{") && line.endsWith("}")) {
        return line;
      }
    }
    return "";
  }
}
