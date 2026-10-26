package com.chengwenfang.sourcing.application;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.chengwenfang.sourcing.application.model.VideoTranscriptCliResult;
import com.chengwenfang.sourcing.facade.enums.VideoParseStep;
import com.chengwenfang.infra.config.ChengwenfangProperties;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 视频转写 CLI 子进程执行器（可测接缝：测试中可替换为读取 fixture 的实现）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoTranscriptCliRunner {
  /** 成文坊配置。 */
  private final ChengwenfangProperties properties;

  /**
   * 调用本机 video_transcript CLI。
   *
   * @param url     视频地址
   * @param workDir 工作目录
   * @param step    解析步骤
   * @return CLI 解析结果
   */
  public VideoTranscriptCliResult run(String url, Path workDir, VideoParseStep step) {
    ChengwenfangProperties.VideoTranscriptSettings settings = properties.getVideoTranscript();
    Path cwd = resolveWorkingDirectory(settings.getWorkingDirectory());
    List<String> command = new ArrayList<>();
    command.add(StrUtil.blankToDefault(settings.getPythonExecutable(), "python"));
    command.add("-m");
    command.add("video_transcript");
    command.add("--url");
    command.add(url);
    command.add("--work-dir");
    command.add(workDir.toAbsolutePath().toString());
    command.add("--step");
    command.add(step.getValue());
    command.add("--lang");
    command.add("zh");
    // 抽帧参数透传
    command.add("--vision-max-frames");
    command.add(String.valueOf(Math.max(1, settings.getVisionMaxFrames())));
    command.add("--scene-threshold");
    command.add(String.valueOf(settings.getSceneThreshold()));
    command.add("--fps-floor-seconds");
    command.add(String.valueOf(Math.max(1, settings.getFpsFloorSeconds())));
    command.add("--dedup-diff-percent");
    command.add(String.valueOf(Math.max(1, settings.getDedupDiffPercent())));
    command.add("--dedup-window");
    command.add(String.valueOf(Math.max(1, settings.getDedupWindow())));
    command.add("--frame-max-width");
    command.add(String.valueOf(Math.max(320, settings.getFrameMaxWidth())));

    // 时长探测仅拉元数据，单独缩短超时
    int timeout = step == VideoParseStep.PROBE_DURATION
        ? Math.max(30, Math.min(120, settings.getStepTimeoutSeconds()))
        : Math.max(60, settings.getStepTimeoutSeconds());
    ProcessBuilder builder = new ProcessBuilder(command);
    builder.directory(cwd.toFile());
    builder.redirectErrorStream(true);
    builder.environment().put("PYTHONIOENCODING", "utf-8");
    builder.environment().put("PYTHONUTF8", "1");
    Process process = null;
    Thread drain = null;
    try {
      process = builder.start();
      StringBuilder output = new StringBuilder();
      Process running = process;
      // 异步排空 stdout，避免先阻塞 readLine 导致 waitFor 超时永远不生效
      drain = new Thread(() -> drainProcessOutput(running, output), "video-cli-drain");
      drain.setDaemon(true);
      drain.start();
      boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        joinQuietly(drain, 3_000L);
        return VideoTranscriptCliResult.builder()
            .ok(false)
            .step(step.getValue())
            .error("CLI 超时（" + timeout + "s）")
            .build();
      }
      joinQuietly(drain, 5_000L);
      String jsonLine;
      synchronized (output) {
        jsonLine = lastJsonLine(output.toString());
      }
      if (StrUtil.isBlank(jsonLine)) {
        String raw;
        synchronized (output) {
          raw = output.toString();
        }
        return VideoTranscriptCliResult.builder()
            .ok(false)
            .step(step.getValue())
            .error("CLI 无 JSON 输出: " + StrUtil.maxLength(raw, 400))
            .build();
      }
      VideoTranscriptCliResult parsed = JSONUtil.toBean(jsonLine, VideoTranscriptCliResult.class);
      if (parsed == null) {
        return VideoTranscriptCliResult.builder()
            .ok(false)
            .step(step.getValue())
            .error("CLI JSON 解析失败")
            .build();
      }
      return parsed;
    } catch (Exception exception) {
      if (process != null && process.isAlive()) {
        process.destroyForcibly();
      }
      joinQuietly(drain, 2_000L);
      log.warn("视频解析 CLI 调用失败 step={} err={}", step.getValue(), exception.getMessage());
      return VideoTranscriptCliResult.builder()
          .ok(false)
          .step(step.getValue())
          .error(exception.getMessage())
          .build();
    }
  }

  /**
   * 持续读取子进程输出到缓冲区。
   *
   * @param process 子进程
   * @param output  输出缓冲（需同步写入）
   */
  private static void drainProcessOutput(Process process, StringBuilder output) {
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        synchronized (output) {
          output.append(line).append('\n');
        }
      }
    } catch (IOException ignored) {
      // 进程被销毁时流关闭属预期
    }
  }

  /**
   * 等待排空线程结束；超时则放弃等待。
   *
   * @param thread         排空线程；可为 null
   * @param timeoutMillis 最长等待毫秒
   */
  private static void joinQuietly(Thread thread, long timeoutMillis) {
    if (thread == null) {
      return;
    }
    try {
      thread.join(timeoutMillis);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * 解析 CLI 工作目录。
   *
   * @param configured 配置路径
   * @return 绝对路径
   */
  private Path resolveWorkingDirectory(String configured) {
    Path path = Path.of(StrUtil.blankToDefault(configured, "scripts/video-transcript"));
    if (path.isAbsolute()) {
      return path.normalize();
    }
    Path fromContentRoot = Path.of(properties.getContentRoot()).resolve(path).toAbsolutePath().normalize();
    if (Files.isDirectory(fromContentRoot)) {
      return fromContentRoot;
    }
    return Path.of(".").resolve(path).toAbsolutePath().normalize();
  }

  /**
   * 取输出中最后一行 JSON。
   *
   * @param output CLI 合并输出
   * @return JSON 行；无则空串
   */
  private static String lastJsonLine(String output) {
    if (StrUtil.isBlank(output)) {
      return "";
    }
    String[] lines = output.split("\n");
    for (int index = lines.length - 1; index >= 0; index--) {
      String line = lines[index].trim();
      if (line.startsWith("{") && line.endsWith("}")) {
        return line;
      }
    }
    return "";
  }
}
