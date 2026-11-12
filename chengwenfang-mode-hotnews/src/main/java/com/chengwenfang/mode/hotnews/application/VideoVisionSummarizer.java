package com.chengwenfang.mode.hotnews.application;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.chengwenfang.ai.facade.AiVisionFacade;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.dto.request.VisionCompleteRequest;
import com.chengwenfang.ai.facade.dto.request.VisionImagePart;
import com.chengwenfang.ai.facade.dto.view.VisionCompleteResult;
import com.chengwenfang.infra.config.ChengwenfangProperties;
import com.chengwenfang.mode.hotnews.application.prompt.VideoVisionPrompts;
import com.chengwenfang.mode.hotnews.facade.VideoParseSink;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsEvidenceKeys;
import com.chengwenfang.sourcing.facade.enums.VideoContentKind;
import com.chengwenfang.sourcing.facade.enums.VideoParseStatus;
import com.chengwenfang.sourcing.facade.enums.VideoParseStep;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 视频关键帧多模态摘要与逐帧描述。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoVisionSummarizer {
  /** 多模态门面。 */
  private final AiVisionFacade vision;
  /** 成文坊配置（视觉超时等）。 */
  private final ChengwenfangProperties properties;

  /**
   * 多模态根据关键帧直接写 summary。
   *
   * @param item    条目
   * @param workDir 工作目录
   */
  public void finishVisionSummarize(VideoParseSink item, Path workDir) {
    List<Path> frames = listFrameFiles(workDir);
    if (frames.isEmpty()) {
      throw new IllegalStateException("未找到关键帧，请重新解析");
    }
    List<VisionImagePart> images = new ArrayList<>();
    for (Path frame : frames) {
      images.add(VisionImagePart.builder()
          .filePath(frame.toAbsolutePath().toString())
          .mimeType("image/jpeg")
          .build());
    }
    VisionCompleteResult result = vision.complete(VisionCompleteRequest.builder()
        .scene(ModelCallScenes.HOT_NEWS_VIDEO_PARSE_VISION_SUMMARIZE)
        .systemPrompt(VideoVisionPrompts.VISION_SUMMARIZE_SYSTEM)
        .prompt("视频标题：" + StrUtil.nullToEmpty(item.getTitle())
            + "\n\n请根据以上关键帧画面，整理中文资讯要点摘要。")
        .images(images)
        .timeoutSeconds(properties.getAiVisionTimeoutSeconds())
        .build());
    String text = result == null ? null : result.getText();
    if (StrUtil.isBlank(text)) {
      throw new IllegalStateException("多模态模型返回空摘要");
    }
    String summary = VideoSpeechSummarizer.toSimplified(text);
    if (summary.length() > 4000) {
      summary = summary.substring(0, 4000);
    }
    item.setSummary(summary);
    item.setVideoRawText(null);
    item.setVideoContentKind(VideoContentKind.VISUAL.getValue());
    item.setVideoParseStatus(VideoParseStatus.SUCCEEDED.getValue());
    item.setVideoParseStep(VideoParseStep.VISION_SUMMARIZE.getValue());
    item.setVideoParseError(null);
    item.setVideoParsedAt(LocalDateTime.now());
    item.persist();
  }

  /**
   * 为工作目录关键帧生成逐帧中文描述；失败返回空列表（不抛）。
   *
   * @param workDir 工作目录
   * @param title   视频标题；可空
   * @return 与帧文件等长的描述；失败为空
   */
  public List<String> captionFramesQuietly(Path workDir, String title) {
    List<Path> allFrames = listFrameFiles(workDir);
    if (allFrames.isEmpty()) {
      return List.of();
    }
    // 与落盘上限对齐，避免多付 Vision 费用且描述与保留帧错位
    List<Path> frames = allFrames.size() <= HotNewsEvidenceKeys.MAX_PERSISTED_VIDEO_FRAMES
        ? allFrames
        : allFrames.subList(0, HotNewsEvidenceKeys.MAX_PERSISTED_VIDEO_FRAMES);
    try {
      List<VisionImagePart> images = new ArrayList<>();
      for (Path frame : frames) {
        images.add(VisionImagePart.builder()
            .filePath(frame.toAbsolutePath().toString())
            .mimeType(frame.getFileName().toString().toLowerCase().endsWith(".png")
                ? "image/png"
                : "image/jpeg")
            .build());
      }
      VisionCompleteResult result = vision.complete(VisionCompleteRequest.builder()
          .scene(ModelCallScenes.HOT_NEWS_VIDEO_FRAME_CAPTION)
          .systemPrompt(VideoVisionPrompts.FRAME_CAPTION_SYSTEM)
          .prompt("视频标题：" + StrUtil.nullToEmpty(title)
              + "\n图片张数：" + frames.size()
              + "\n请按图片顺序输出 JSON 字符串数组。")
          .images(images)
          .timeoutSeconds(properties.getAiVisionTimeoutSeconds())
          .build());
      return VideoParseRunService.alignCaptions(
          frames.size(), parseCaptionArray(result == null ? null : result.getText()));
    } catch (Exception exception) {
      log.warn("视频逐帧描述失败 frames={} err={}", frames.size(), exception.getMessage());
      return List.of();
    }
  }

  /**
   * 工作目录下是否已有关键帧文件。
   *
   * @param workDir 工作目录
   * @return 有帧文件时为 true
   */
  public static boolean hasFrameFiles(Path workDir) {
    return !listFrameFiles(workDir).isEmpty();
  }

  /**
   * 解析模型返回的描述数组。
   *
   * @param raw 模型原文
   * @return 描述列表
   */
  static List<String> parseCaptionArray(String raw) {
    if (StrUtil.isBlank(raw)) {
      return List.of();
    }
    String text = raw.trim();
    int start = text.indexOf('[');
    int end = text.lastIndexOf(']');
    if (start < 0 || end <= start) {
      return List.of();
    }
    try {
      JSONArray array = JSONUtil.parseArray(text.substring(start, end + 1));
      List<String> captions = new ArrayList<>();
      for (Object item : array) {
        captions.add(StrUtil.trim(StrUtil.nullToEmpty(item == null ? null : String.valueOf(item))));
      }
      return captions;
    } catch (Exception ignored) {
      return List.of();
    }
  }

  /**
   * 列出关键帧图片路径（按文件名排序）。
   *
   * @param workDir 工作目录
   * @return 帧文件列表；无目录时为空列表
   */
  public static List<Path> listFrameFiles(Path workDir) {
    Path framesDir = workDir.resolve("frames");
    if (!Files.isDirectory(framesDir)) {
      return List.of();
    }
    try (Stream<Path> stream = Files.list(framesDir)) {
      return stream
          .filter(Files::isRegularFile)
          .filter(path -> {
            String name = path.getFileName().toString().toLowerCase();
            return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png");
          })
          .sorted(Comparator.comparing(path -> path.getFileName().toString()))
          .toList();
    } catch (Exception exception) {
      return List.of();
    }
  }
}
