package com.workbench.mode.hotnews.application;

import cn.hutool.core.util.StrUtil;
import com.workbench.ai.facade.AiTextFacade;
import com.workbench.ai.facade.constant.ModelCallScenes;
import com.workbench.ai.facade.dto.request.TextCompleteRequest;
import com.workbench.ai.facade.dto.view.TextCompleteResult;
import com.workbench.mode.hotnews.application.prompt.VideoSpeechPrompts;
import com.workbench.mode.hotnews.domain.ChineseTextNormalizer;
import com.workbench.mode.hotnews.facade.VideoParseSink;
import com.workbench.sourcing.facade.enums.VideoContentKind;
import com.workbench.sourcing.facade.enums.VideoParseStatus;
import com.workbench.sourcing.facade.enums.VideoParseStep;
import java.time.LocalDateTime;
import java.util.function.BiConsumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 视频口播/字幕文本摘要（文本模型）。
 */
@Component
@RequiredArgsConstructor
public class VideoSpeechSummarizer {
  /** 文本模型门面。 */
  private final AiTextFacade ai;
  /** 解析状态落库。 */
  private final VideoTranscriptStore store;

  /**
   * 文本总结并标记语音类型成功。
   *
   * @param item         条目
   * @param rawText      原文
   * @param progressHook 进度回调
   */
  public void finishWithSpeechSummarize(
      VideoParseSink item, String rawText, BiConsumer<String, String> progressHook) {
    VideoParseStep step = VideoParseStep.SUMMARIZE;
    notify(progressHook, step.getValue(), step.getLabel() + "：" + displayName(item));
    store.markRunning(item, step);
    try {
      finishSpeechSummarize(item, rawText);
    } catch (Exception exception) {
      store.fail(item, step, "AI 总结失败: " + exception.getMessage());
    }
  }

  /**
   * 完成语音摘要并标记成功。
   *
   * @param item    条目
   * @param rawText 口播/字幕原文
   */
  public void finishSpeechSummarize(VideoParseSink item, String rawText) {
    String summary = summarizeSpeech(rawText, item.getTitle());
    item.setSummary(summary);
    item.setVideoRawText(rawText);
    item.setVideoContentKind(VideoContentKind.SPEECH.getValue());
    item.setVideoParseStatus(VideoParseStatus.SUCCEEDED.getValue());
    item.setVideoParseStep(VideoParseStep.SUMMARIZE.getValue());
    item.setVideoParseError(null);
    item.setVideoParsedAt(LocalDateTime.now());
    item.persist();
  }

  /**
   * 调用文本模型生成口播摘要。
   *
   * @param rawText 原文
   * @param title   视频标题
   * @return 简体中文摘要
   */
  public String summarizeSpeech(String rawText, String title) {
    String clipped = rawText.length() > 12000 ? rawText.substring(0, 12000) : rawText;
    TextCompleteResult result = ai.completeText(TextCompleteRequest.builder()
        .scene(ModelCallScenes.HOT_NEWS_VIDEO_PARSE_SUMMARIZE)
        .systemPrompt(VideoSpeechPrompts.SUMMARIZE_SYSTEM)
        .prompt("视频标题：" + StrUtil.nullToEmpty(title) + "\n\n原文：\n" + clipped)
        .timeoutSeconds(120)
        .build());
    String text = result == null ? null : result.getText();
    if (StrUtil.isBlank(text)) {
      throw new IllegalStateException("模型返回空摘要");
    }
    String summary = toSimplified(text);
    if (summary.length() > 4000) {
      summary = summary.substring(0, 4000);
    }
    return summary;
  }

  /**
   * 转为简体并去首尾空白。
   *
   * @param text 原文
   * @return 简体文本
   */
  static String toSimplified(String text) {
    String simplified = ChineseTextNormalizer.toSimplified(StrUtil.nullToEmpty(text).trim());
    return simplified == null ? "" : simplified.trim();
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
