package com.chengwenfang.mode.hotnews.application;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.ai.facade.AiVisionFacade;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.dto.request.VisionCompleteRequest;
import com.chengwenfang.ai.facade.dto.request.VisionImagePart;
import com.chengwenfang.ai.facade.dto.view.VisionCompleteResult;
import com.chengwenfang.infra.config.ChengwenfangProperties;
import com.chengwenfang.mode.hotnews.application.prompt.VideoVisionPrompts;
import com.chengwenfang.mode.hotnews.facade.VideoParseSink;
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
import org.springframework.stereotype.Component;

/**
 * 视频关键帧多模态摘要。
 */
@Component
@RequiredArgsConstructor
public class VideoVisionSummarizer {
  /** 多模态门面。 */
  private final AiVisionFacade vision;
  /** 工作台配置（视觉超时等）。 */
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
   * 工作目录下是否已有关键帧文件。
   *
   * @param workDir 工作目录
   * @return 有帧文件时为 true
   */
  public static boolean hasFrameFiles(Path workDir) {
    return !listFrameFiles(workDir).isEmpty();
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
