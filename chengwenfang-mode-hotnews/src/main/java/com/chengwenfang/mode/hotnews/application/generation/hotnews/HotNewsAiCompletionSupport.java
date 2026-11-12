package com.chengwenfang.mode.hotnews.application.generation.hotnews;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.ai.facade.AiVisionFacade;
import com.chengwenfang.ai.facade.dto.request.VisionCompleteRequest;
import com.chengwenfang.prompt.facade.PromptPackFacade;
import com.chengwenfang.prompt.facade.dto.view.PromptPackPromptBindingView;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 热点补全节点共用：组合提示词包并调用多模态补全。
 */
@Component
@RequiredArgsConstructor
public class HotNewsAiCompletionSupport {
  /** 提示词包 组合。 */
  private final PromptPackFacade promptPacks;
  /** 多模态补全门面。 */
  private final AiVisionFacade ai;

  /**
   * 组合绑定提示词包 正文。
   *
   * @param promptPackBindings 节点绑定
   * @return 提示词包 提示词
   */
  public String composePromptPackText(List<PromptPackPromptBindingView> promptPackBindings) {
    return promptPacks.composePromptFromBindings(promptPackBindings == null ? List.of() : promptPackBindings);
  }

  /**
   * 调用多模态补全。
   *
   * @param systemPrompt 系统提示
   * @param userPrompt   用户提示
   * @param scene        审计场景
   * @return 非空正文
   */
  public String complete(String systemPrompt, String userPrompt, String scene) {
    var result = ai.complete(VisionCompleteRequest.builder()
        .scene(scene)
        .systemPrompt(systemPrompt)
        .prompt(userPrompt)
        .timeoutSeconds(300)
        .build());
    String text = result == null ? "" : StrUtil.trim(StrUtil.nullToEmpty(result.getText()));
    if (StrUtil.isBlank(text)) {
      throw new IllegalStateException("热点节点输出为空 scene=" + scene);
    }
    return text;
  }
}
