package com.workbench.app.golden.stub;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.workbench.crawler.application.article.LightpandaArticleExtractor;
import com.workbench.crawler.application.article.TrafilaturaArticleExtractor;
import com.workbench.crawler.application.article.TrafilaturaCliResult;
import com.workbench.sourcing.application.VideoTranscriptCliRunner;
import com.workbench.sourcing.application.model.VideoTranscriptCliResult;
import com.workbench.sourcing.facade.enums.VideoParseStep;
import com.workbench.infra.config.WorkbenchProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 黄金测试替身：视频转写 CLI、Trafilatura、Lightpanda（读取 {@code golden/cli-fixtures}）。
 */
public final class GoldenTestBeans {
  /** 1×1 PNG。 */
  private static final String TINY_PNG_B64 =
      "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

  private GoldenTestBeans() {}

  /**
   * @return 类名
   */
  public String placeholderName() {
    return "GoldenTestBeans";
  }

  /**
   * 固定 Trafilatura 抽取结果。
   *
   * @return 替身
   */
  public static TrafilaturaArticleExtractor fixedTrafilatura() {
    return url -> loadExtractResult("trafilatura-extract.json");
  }

  /**
   * 固定 Lightpanda 抽取结果。
   *
   * @return 替身
   */
  public static LightpandaArticleExtractor fixedLightpanda() {
    return url -> loadExtractResult("lightpanda-extract.json");
  }

  /**
   * 按步骤返回固定 CLI JSON；抽帧时在 workDir 写入关键帧 PNG。
   *
   * @param properties 原配置（仅满足构造；run 不启子进程）
   * @return 替身 Runner
   */
  public static VideoTranscriptCliRunner fixtureVideoTranscriptCliRunner(
      WorkbenchProperties properties) {
    return new VideoTranscriptCliRunner(properties) {
      @Override
      public VideoTranscriptCliResult run(String url, Path workDir, VideoParseStep step) {
        VideoParseStep resolved = step == null ? VideoParseStep.FETCH_SUBTITLE : step;
        if (!resolved.isCliStep()) {
          return VideoTranscriptCliResult.builder()
              .ok(false)
              .step(resolved.getValue())
              .error("非 CLI 步骤，应由 Java 侧处理")
              .build();
        }
        VideoTranscriptCliResult result = loadCliResult(resolved.getValue());
        if (resolved == VideoParseStep.EXTRACT_FRAMES && workDir != null) {
          Path frame = workDir.resolve("frames").resolve("frame-001.png");
          FileUtil.mkdir(frame.getParent().toFile());
          FileUtil.writeBytes(Base64.getDecoder().decode(TINY_PNG_B64), frame.toFile());
          Map<String, Object> artifacts = new LinkedHashMap<>();
          if (result.getArtifacts() != null) {
            artifacts.putAll(result.getArtifacts());
          }
          artifacts.put("frames_dir", workDir.resolve("frames").toAbsolutePath().toString());
          artifacts.put("frame_paths", java.util.List.of(frame.toAbsolutePath().toString()));
          result.setArtifacts(artifacts);
        }
        return result;
      }
    };
  }

  private static TrafilaturaCliResult loadExtractResult(String fileName) {
    String raw = ResourceUtil.readStr("golden/cli-fixtures/" + fileName, StandardCharsets.UTF_8);
    TrafilaturaCliResult result = JSONUtil.toBean(raw, TrafilaturaCliResult.class);
    if (result == null || !result.isOk() || StrUtil.isBlank(result.getText())) {
      throw new IllegalStateException("无效 extract fixture: " + fileName);
    }
    return result;
  }

  private static VideoTranscriptCliResult loadCliResult(String step) {
    String path = "golden/cli-fixtures/video-" + step + ".json";
    String raw = ResourceUtil.readStr(path, StandardCharsets.UTF_8);
    if (StrUtil.isBlank(raw)) {
      throw new IllegalStateException("缺少 CLI fixture: " + path);
    }
    JSONObject json = JSONUtil.parseObj(raw);
    VideoTranscriptCliResult result = json.toBean(VideoTranscriptCliResult.class);
    if (result == null) {
      throw new IllegalStateException("无法解析 CLI fixture: " + path);
    }
    return result;
  }
}
