package com.chengwenfang.mode.hotnews.application;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.ai.facade.AiVisionFacade;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.dto.request.VisionCompleteRequest;
import com.chengwenfang.ai.facade.dto.request.VisionImagePart;
import com.chengwenfang.ai.facade.dto.view.VisionCompleteResult;
import com.chengwenfang.infra.config.ChengwenfangProperties;
import com.chengwenfang.mode.hotnews.application.prompt.ResearchArticleVisionBriefPrompts;
import com.chengwenfang.mode.hotnews.domain.ChineseTextNormalizer;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 资料补充：网页截图多模态事实摘要。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResearchArticleVisionSummarizer {

  /** 摘要入库上限。 */
  static final int SUMMARY_MAX_CHARS = 4000;
  /** PNG MIME。 */
  private static final String MIME_PNG = "image/png";

  /** 多模态门面。 */
  private final AiVisionFacade vision;
  /** 成文坊配置（视觉超时等）。 */
  private final ChengwenfangProperties properties;

  /**
   * 根据截图路径生成事实摘要（简体、截断）。
   *
   * @param title 网页标题
   * @param paths PNG 绝对路径
   * @return 摘要；失败空串
   */
  public String summarize(String title, List<String> paths) {
    List<VisionImagePart> images = new ArrayList<>();
    if (paths != null) {
      for (String path : paths) {
        if (StrUtil.isBlank(path)) {
          continue;
        }
        images.add(VisionImagePart.builder()
            .filePath(path)
            .mimeType(MIME_PNG)
            .build());
      }
    }
    if (images.isEmpty()) {
      return "";
    }
    try {
      // 调用多模态：按截图识别正文事实
      VisionCompleteResult result = vision.complete(VisionCompleteRequest.builder()
          .scene(ModelCallScenes.HOT_NEWS_TOPIC_RESEARCH_ARTICLE_VISION_BRIEF)
          .systemPrompt(ResearchArticleVisionBriefPrompts.SYSTEM)
          .prompt("网页标题：" + StrUtil.nullToEmpty(title)
              + "\n\n请根据以上网页截图，整理中文事实摘要（忽略导航与广告）。")
          .images(images)
          .timeoutSeconds(properties.getAiVisionTimeoutSeconds())
          .build());
      String text = result == null ? "" : StrUtil.trim(result.getText());
      if (StrUtil.isBlank(text)) {
        return "";
      }
      String simplified = ChineseTextNormalizer.toSimplified(text);
      if (simplified.length() > SUMMARY_MAX_CHARS) {
        return simplified.substring(0, SUMMARY_MAX_CHARS);
      }
      return simplified;
    } catch (Exception exception) {
      log.warn("资料补充 Vision 摘要失败 title={} err={}", title, exception.getMessage());
      return "";
    }
  }
}
