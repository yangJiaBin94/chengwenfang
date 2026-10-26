package com.chengwenfang.mode.hotnews.application;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.infra.config.ChengwenfangProperties;
import com.chengwenfang.mode.hotnews.facade.VideoParseSink;
import com.chengwenfang.sourcing.application.VideoSpeechTextQuality;
import com.chengwenfang.sourcing.application.VideoTranscriptCliRunner;
import com.chengwenfang.sourcing.application.model.VideoTranscriptCliResult;
import com.chengwenfang.sourcing.facade.enums.VideoContentKind;
import com.chengwenfang.sourcing.facade.enums.VideoParseStep;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 视频解析步骤编排：语音优先链路与画面链路。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoTranscriptPipeline {
  /** 工作台配置。 */
  private final ChengwenfangProperties properties;
  /** CLI 子进程执行接缝。 */
  private final VideoTranscriptCliRunner cliRunner;
  /** 口播文本摘要。 */
  private final VideoSpeechSummarizer speechSummarizer;
  /** 关键帧视觉摘要。 */
  private final VideoVisionSummarizer visionSummarizer;
  /** 解析状态落库。 */
  private final VideoTranscriptStore store;
  /** 时长缓存与探测。 */
  private final VideoDurationCache durationCache;

  /**
   * 清理 {@code runtimeDir/video-transcript} 下超过保留天数的条目工作目录。
   *
   * @return 删除的子目录个数
   */
  public int cleanupExpiredWorkDirs() {
    ChengwenfangProperties.VideoTranscriptSettings settings = properties.getVideoTranscript();
    int retainDays = settings == null ? 3 : settings.getWorkDirRetainDays();
    if (retainDays <= 0) {
      retainDays = 3;
    }
    Path root = Path.of(properties.getRuntimeDir()).toAbsolutePath().normalize()
        .resolve("video-transcript");
    return cleanupExpiredWorkDirs(root, retainDays);
  }

  /**
   * 清理指定根目录下超期子目录（供单测）。
   *
   * @param root       video-transcript 根目录
   * @param retainDays 保留天数
   * @return 删除的子目录个数
   */
  int cleanupExpiredWorkDirs(Path root, int retainDays) {
    if (root == null || retainDays <= 0 || !Files.isDirectory(root)) {
      return 0;
    }
    Instant cutoff = Instant.now().minus(retainDays, ChronoUnit.DAYS);
    int removed = 0;
    try (Stream<Path> children = Files.list(root)) {
      for (Path child : children.toList()) {
        if (!Files.isDirectory(child)) {
          continue;
        }
        Instant newest = newestActivityInstant(child);
        if (newest == null || newest.isBefore(cutoff)) {
          clearWorkDir(child);
          try {
            Files.deleteIfExists(child);
            removed++;
          } catch (Exception exception) {
            log.warn("删除视频解析工作目录失败 path={} err={}", child, exception.getMessage());
          }
        }
      }
    } catch (Exception exception) {
      log.warn("扫描视频解析工作目录失败 root={} err={}", root, exception.getMessage());
    }
    return removed;
  }

  /**
   * 对可写目标执行字幕/ASR 或画面解析流水线。
   *
   * @param sink         落库目标
   * @param fromStep     起始步骤
   * @param progressHook 进度回调
   */
  public void parseSink(
      VideoParseSink sink,
      VideoParseStep fromStep,
      BiConsumer<String, String> progressHook) {
    if (sink == null || StrUtil.isBlank(sink.getUrl())) {
      throw new IllegalArgumentException("视频资料缺少链接");
    }
    if (!durationCache.isEnabled()) {
      store.fail(sink, VideoParseStep.FETCH_SUBTITLE, "视频解析未启用（chengwenfang.video-transcript.enabled=false）");
      return;
    }

    Path workDir = workDirFor(sink.getId());
    try {
      Files.createDirectories(workDir);
    } catch (Exception exception) {
      store.fail(sink, VideoParseStep.FETCH_SUBTITLE, "无法创建工作目录: " + exception.getMessage());
      return;
    }

    // 下载/ASR 前按配置拦截超长视频；探测结果同步落库便于资料池展示
    String durationReject = durationCache.durationLimitRejectReason(sink.getUrl());
    Integer durationSeconds = VideoDurationCache.toRoundedDurationSeconds(
        durationCache.peekCachedDurationSeconds(sink.getUrl()));
    if (durationSeconds != null) {
      sink.setVideoDurationSeconds(durationSeconds);
    }
    if (StrUtil.isNotBlank(durationReject)) {
      notify(progressHook, VideoParseStep.PROBE_DURATION.getValue(), durationReject);
      store.fail(sink, VideoParseStep.PROBE_DURATION, durationReject);
      return;
    }

    boolean visual = VideoContentKind.isVisual(sink.getVideoContentKind());

    // 显式从头解析：语音链路清空全部产物；画面链路只清视频/帧且保留 kind
    if (fromStep == VideoParseStep.FETCH_SUBTITLE && !visual) {
      clearWorkDir(workDir);
      sink.setVideoRawText(null);
      sink.persist();
    } else if (visual && (fromStep == null
        || fromStep == VideoParseStep.DOWNLOAD_VIDEO
        || fromStep == VideoParseStep.FETCH_SUBTITLE)) {
      clearVisualArtifacts(workDir);
      fromStep = VideoParseStep.DOWNLOAD_VIDEO;
    }

    if (visual) {
      runVisualPipeline(sink, fromStep, workDir, progressHook);
      return;
    }

    runSpeechThenMaybeVisual(sink, fromStep, workDir, progressHook);
  }

  /**
   * 语音优先链路；口播无效时打标并转入画面链路。
   *
   * @param item         条目
   * @param fromStep     起始步骤
   * @param workDir      工作目录
   * @param progressHook 进度回调
   */
  private void runSpeechThenMaybeVisual(
      VideoParseSink item,
      VideoParseStep fromStep,
      Path workDir,
      BiConsumer<String, String> progressHook) {
    VideoParseStep start = resolveSpeechStartStep(item, fromStep, workDir);
    // 若起点已在画面段，直接转画面
    if (start == VideoParseStep.DOWNLOAD_VIDEO
        || start == VideoParseStep.EXTRACT_FRAMES
        || start == VideoParseStep.VISION_SUMMARIZE) {
      markVisualKind(item);
      runVisualPipeline(item, start, workDir, progressHook);
      return;
    }

    store.markRunning(item, start);
    String rawText = StrUtil.nullToEmpty(item.getVideoRawText()).trim();
    List<VideoParseStep> speechSteps = List.of(
        VideoParseStep.FETCH_SUBTITLE,
        VideoParseStep.DOWNLOAD_AUDIO,
        VideoParseStep.CONVERT_FORMAT,
        VideoParseStep.ASR,
        VideoParseStep.SUMMARIZE);

    boolean started = false;
    for (VideoParseStep step : speechSteps) {
      if (step == start) {
        started = true;
      }
      if (!started) {
        continue;
      }

      notify(progressHook, step.getValue(), step.getLabel() + "：" + displayName(item));
      store.markRunning(item, step);

      if (step == VideoParseStep.SUMMARIZE) {
        if (StrUtil.isBlank(rawText)) {
          rawText = readRawFile(workDir);
        }
        if (!VideoSpeechTextQuality.isValidSpeechText(rawText)) {
          markVisualKind(item);
          runVisualPipeline(item, VideoParseStep.DOWNLOAD_VIDEO, workDir, progressHook);
          return;
        }
        try {
          speechSummarizer.finishSpeechSummarize(item, rawText);
          return;
        } catch (Exception exception) {
          store.fail(item, step, "AI 总结失败: " + exception.getMessage());
          return;
        }
      }

      if (step == VideoParseStep.FETCH_SUBTITLE) {
        VideoTranscriptCliResult result = cliRunner.run(item.getUrl(), workDir, step);
        if (result.isOk()
            && VideoSpeechTextQuality.isValidSpeechText(result.getText())) {
          rawText = VideoSpeechSummarizer.toSimplified(result.getText());
          item.setVideoRawText(rawText);
          item.setVideoContentKind(VideoContentKind.SPEECH.getValue());
          item.persist();
          speechSummarizer.finishWithSpeechSummarize(item, rawText, progressHook);
          return;
        }
        // 无有效字幕：继续 ASR
        continue;
      }

      VideoTranscriptCliResult result = cliRunner.run(item.getUrl(), workDir, step);
      if (!result.isOk()) {
        if (step == VideoParseStep.ASR) {
          markVisualKind(item);
          runVisualPipeline(item, VideoParseStep.DOWNLOAD_VIDEO, workDir, progressHook);
          return;
        }
        store.fail(item, step, StrUtil.blankToDefault(result.getError(), "步骤失败"));
        return;
      }
      if (step == VideoParseStep.ASR) {
        String asrText = VideoSpeechSummarizer.toSimplified(StrUtil.nullToEmpty(result.getText()));
        if (!VideoSpeechTextQuality.isValidSpeechText(asrText)) {
          markVisualKind(item);
          runVisualPipeline(item, VideoParseStep.DOWNLOAD_VIDEO, workDir, progressHook);
          return;
        }
        rawText = asrText;
        item.setVideoRawText(rawText);
        item.setVideoContentKind(VideoContentKind.SPEECH.getValue());
        item.persist();
      }
    }
  }

  /**
   * 画面链路：下载视频 → 抽帧 → 多模态直接出总结。
   *
   * @param item         条目
   * @param fromStep     起始步骤
   * @param workDir      工作目录
   * @param progressHook 进度回调
   */
  private void runVisualPipeline(
      VideoParseSink item,
      VideoParseStep fromStep,
      Path workDir,
      BiConsumer<String, String> progressHook) {
    markVisualKind(item);
    VideoParseStep start = resolveVisualStartStep(fromStep, workDir);
    List<VideoParseStep> pipeline = VideoParseStep.visualPipelineFrom(start);

    for (VideoParseStep step : pipeline) {
      notify(progressHook, step.getValue(), step.getLabel() + "：" + displayName(item));
      store.markRunning(item, step);

      if (step == VideoParseStep.VISION_SUMMARIZE) {
        try {
          visionSummarizer.finishVisionSummarize(item, workDir);
          return;
        } catch (Exception exception) {
          store.fail(item, step, "画面理解失败: " + exception.getMessage());
          return;
        }
      }

      VideoTranscriptCliResult result = cliRunner.run(item.getUrl(), workDir, step);
      if (!result.isOk()) {
        store.fail(item, step, StrUtil.blankToDefault(result.getError(), "步骤失败"));
        return;
      }
    }
  }

  /**
   * 打标为画面类型（若尚未标记）。
   *
   * @param item 条目
   */
  private void markVisualKind(VideoParseSink item) {
    if (!VideoContentKind.isVisual(item.getVideoContentKind())) {
      item.setVideoContentKind(VideoContentKind.VISUAL.getValue());
      item.persist();
    }
  }

  /**
   * 推断语音链路实际起点（含产物缺失回退）。
   *
   * @param item     条目
   * @param fromStep 请求起点
   * @param workDir  工作目录
   * @return 可执行步骤
   */
  private VideoParseStep resolveSpeechStartStep(
      VideoParseSink item, VideoParseStep fromStep, Path workDir) {
    VideoParseStep requested = fromStep;
    if (requested == null) {
      if (Files.isDirectory(workDir.resolve("frames")) && VideoVisionSummarizer.hasFrameFiles(workDir)) {
        requested = VideoParseStep.VISION_SUMMARIZE;
      } else if (Files.isRegularFile(workDir.resolve("video.mp4"))) {
        requested = VideoParseStep.EXTRACT_FRAMES;
      } else if (StrUtil.isNotBlank(item.getVideoRawText())
          && VideoSpeechTextQuality.isValidSpeechText(item.getVideoRawText())) {
        requested = VideoParseStep.SUMMARIZE;
      } else if (Files.isRegularFile(workDir.resolve("raw.txt"))) {
        String diskRaw = readRawFile(workDir);
        requested = VideoSpeechTextQuality.isValidSpeechText(diskRaw)
            ? VideoParseStep.SUMMARIZE
            : VideoParseStep.FETCH_SUBTITLE;
      } else if (Files.isRegularFile(workDir.resolve("audio.mp3"))) {
        requested = VideoParseStep.ASR;
      } else {
        requested = VideoParseStep.FETCH_SUBTITLE;
      }
    }
    return rewindSpeechIfArtifactsMissing(item, requested, workDir);
  }

  /**
   * 画面链路起点：产物缺失时回退到更早可重新下载/抽帧的步骤。
   *
   * @param fromStep 请求起点
   * @param workDir  工作目录
   * @return 实际起点
   */
  private VideoParseStep resolveVisualStartStep(VideoParseStep fromStep, Path workDir) {
    VideoParseStep requested = fromStep == null || fromStep == VideoParseStep.FETCH_SUBTITLE
        ? VideoParseStep.DOWNLOAD_VIDEO
        : fromStep;
    if (requested != VideoParseStep.DOWNLOAD_VIDEO
        && requested != VideoParseStep.EXTRACT_FRAMES
        && requested != VideoParseStep.VISION_SUMMARIZE) {
      requested = VideoParseStep.DOWNLOAD_VIDEO;
    }
    return rewindVisualIfArtifactsMissing(requested, workDir);
  }

  /**
   * 语音链路：请求步骤依赖的本地产物不存在时回退，必要时重新下载。
   *
   * @param item      条目
   * @param requested 请求步骤
   * @param workDir   工作目录
   * @return 可执行步骤
   */
  private VideoParseStep rewindSpeechIfArtifactsMissing(
      VideoParseSink item, VideoParseStep requested, Path workDir) {
    VideoParseStep step = requested == null ? VideoParseStep.FETCH_SUBTITLE : requested;
    int guard = 0;
    while (guard++ < 8) {
      if (speechStepArtifactsReady(item, step, workDir)) {
        if (step != requested) {
          log.info(
              "视频解析产物缺失，回退语音步骤 itemId={} from={} to={}",
              item.getId(),
              requested.getValue(),
              step.getValue());
        }
        return step;
      }
      VideoParseStep earlier = earlierSpeechStep(step);
      if (earlier == null || earlier == step) {
        return VideoParseStep.FETCH_SUBTITLE;
      }
      step = earlier;
    }
    return VideoParseStep.FETCH_SUBTITLE;
  }

  /**
   * 画面链路：关键帧/视频缺失时回退到抽帧或下载。
   *
   * @param requested 请求步骤
   * @param workDir   工作目录
   * @return 可执行步骤
   */
  private VideoParseStep rewindVisualIfArtifactsMissing(VideoParseStep requested, Path workDir) {
    VideoParseStep step = requested == null ? VideoParseStep.DOWNLOAD_VIDEO : requested;
    int guard = 0;
    while (guard++ < 6) {
      if (visualStepArtifactsReady(step, workDir)) {
        if (step != requested) {
          log.info(
              "视频解析产物缺失，回退画面步骤 from={} to={}",
              requested.getValue(),
              step.getValue());
        }
        return step;
      }
      VideoParseStep earlier = earlierVisualStep(step);
      if (earlier == null || earlier == step) {
        return VideoParseStep.DOWNLOAD_VIDEO;
      }
      step = earlier;
    }
    return VideoParseStep.DOWNLOAD_VIDEO;
  }

  /**
   * 语音步骤依赖的本地产物是否就绪。
   *
   * @param item    条目
   * @param step    步骤
   * @param workDir 工作目录
   * @return 就绪时为 true
   */
  private boolean speechStepArtifactsReady(
      VideoParseSink item, VideoParseStep step, Path workDir) {
    if (step == VideoParseStep.SUMMARIZE) {
      if (StrUtil.isNotBlank(item.getVideoRawText())
          && VideoSpeechTextQuality.isValidSpeechText(item.getVideoRawText())) {
        return true;
      }
      return VideoSpeechTextQuality.isValidSpeechText(readRawFile(workDir));
    }
    if (step == VideoParseStep.ASR) {
      return Files.isRegularFile(workDir.resolve("audio.mp3"));
    }
    if (step == VideoParseStep.CONVERT_FORMAT) {
      return hasAnyAudioArtifact(workDir);
    }
    return true;
  }

  /**
   * 画面步骤依赖的本地产物是否就绪。
   *
   * @param step    步骤
   * @param workDir 工作目录
   * @return 就绪时为 true
   */
  private boolean visualStepArtifactsReady(VideoParseStep step, Path workDir) {
    if (step == VideoParseStep.VISION_SUMMARIZE) {
      return VideoVisionSummarizer.hasFrameFiles(workDir);
    }
    if (step == VideoParseStep.EXTRACT_FRAMES) {
      return Files.isRegularFile(workDir.resolve("video.mp4"));
    }
    return true;
  }

  /**
   * 语音链路更早一步。
   *
   * @param step 当前步骤
   * @return 更早步骤
   */
  private static VideoParseStep earlierSpeechStep(VideoParseStep step) {
    if (step == VideoParseStep.SUMMARIZE) {
      return VideoParseStep.ASR;
    }
    if (step == VideoParseStep.ASR) {
      return VideoParseStep.CONVERT_FORMAT;
    }
    if (step == VideoParseStep.CONVERT_FORMAT) {
      return VideoParseStep.DOWNLOAD_AUDIO;
    }
    if (step == VideoParseStep.DOWNLOAD_AUDIO) {
      return VideoParseStep.FETCH_SUBTITLE;
    }
    return VideoParseStep.FETCH_SUBTITLE;
  }

  /**
   * 画面链路更早一步。
   *
   * @param step 当前步骤
   * @return 更早步骤
   */
  private static VideoParseStep earlierVisualStep(VideoParseStep step) {
    if (step == VideoParseStep.VISION_SUMMARIZE) {
      return VideoParseStep.EXTRACT_FRAMES;
    }
    if (step == VideoParseStep.EXTRACT_FRAMES) {
      return VideoParseStep.DOWNLOAD_VIDEO;
    }
    return VideoParseStep.DOWNLOAD_VIDEO;
  }

  /**
   * 工作目录是否已有任意音频产物。
   *
   * @param workDir 工作目录
   * @return 有音频文件时为 true
   */
  private static boolean hasAnyAudioArtifact(Path workDir) {
    if (workDir == null || !Files.isDirectory(workDir)) {
      return false;
    }
    try (Stream<Path> stream = Files.list(workDir)) {
      return stream
          .filter(Files::isRegularFile)
          .map(path -> path.getFileName().toString().toLowerCase())
          .anyMatch(name ->
              name.endsWith(".m4a")
                  || name.endsWith(".webm")
                  || name.endsWith(".opus")
                  || name.endsWith(".wav")
                  || name.endsWith(".mp3"));
    } catch (Exception ignored) {
      return false;
    }
  }

  /**
   * 取目录内最新活动时间（文件优先）；空目录用目录自身时间。
   *
   * @param dir 条目工作目录
   * @return 最新时间；无法读取时 null
   */
  private static Instant newestActivityInstant(Path dir) {
    java.util.concurrent.atomic.AtomicReference<Instant> newest =
        new java.util.concurrent.atomic.AtomicReference<>();
    try {
      FileTime dirTime = Files.getLastModifiedTime(dir);
      newest.set(dirTime.toInstant());
    } catch (Exception ignored) {
      // 继续扫文件
    }
    try {
      Files.walkFileTree(
          dir,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
              Instant fileInstant = attrs.lastModifiedTime().toInstant();
              Instant current = newest.get();
              if (current == null || fileInstant.isAfter(current)) {
                newest.set(fileInstant);
              }
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (Exception ignored) {
      // 用目录时间兜底
    }
    return newest.get();
  }

  /**
   * 条目工作目录路径。
   *
   * @param itemId 条目 ID
   * @return 工作目录
   */
  private Path workDirFor(String itemId) {
    Path runtime = Path.of(properties.getRuntimeDir()).toAbsolutePath().normalize();
    return runtime.resolve("video-transcript").resolve(itemId);
  }

  /**
   * 读取工作目录 raw.txt。
   *
   * @param workDir 工作目录
   * @return 文件内容；不存在或失败时为空串
   */
  private static String readRawFile(Path workDir) {
    try {
      Path raw = workDir.resolve("raw.txt");
      if (Files.isRegularFile(raw)) {
        return Files.readString(raw, StandardCharsets.UTF_8).trim();
      }
    } catch (Exception ignored) {
      // ignore
    }
    return "";
  }

  /**
   * 清空工作目录内全部文件与子目录（保留根目录）。
   *
   * @param workDir 工作目录
   */
  private static void clearWorkDir(Path workDir) {
    if (workDir == null || !Files.isDirectory(workDir)) {
      return;
    }
    try {
      Files.walkFileTree(
          workDir,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
              Files.deleteIfExists(file);
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
              if (!dir.equals(workDir)) {
                Files.deleteIfExists(dir);
              }
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (Exception ignored) {
      // 清理失败时后续步骤仍可覆盖写入
    }
  }

  /** 仅清理画面链路产物，保留音频/字幕以便对照。 */
  private static void clearVisualArtifacts(Path workDir) {
    if (workDir == null || !Files.isDirectory(workDir)) {
      return;
    }
    try {
      Files.deleteIfExists(workDir.resolve("video.mp4"));
      Path frames = workDir.resolve("frames");
      if (Files.isDirectory(frames)) {
        try (Stream<Path> stream = Files.list(frames)) {
          stream.forEach(path -> {
            try {
              Files.deleteIfExists(path);
            } catch (Exception ignored) {
              // ignore
            }
          });
        }
        Files.deleteIfExists(frames);
      }
      Files.deleteIfExists(workDir.resolve("frames_manifest.json"));
    } catch (Exception ignored) {
      // ignore
    }
  }

  /**
   * 展示用名称：标题优先，否则 URL。
   *
   * @param item 条目
   * @return 展示文本
   */
  private static String displayName(VideoParseSink item) {
    return StrUtil.blankToDefault(item.getTitle(), item.getUrl());
  }

  /**
   * 安全触发进度回调。
   *
   * @param hook    回调；可为 null
   * @param step    步骤键
   * @param message 进度文案
   */
  private static void notify(BiConsumer<String, String> hook, String step, String message) {
    if (hook != null) {
      hook.accept(step, message);
    }
  }
}
