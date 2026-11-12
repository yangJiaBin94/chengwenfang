package com.chengwenfang.mode.hotnews.application.generation.hotnews;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.ai.facade.AiTextFacade;
import com.chengwenfang.ai.facade.dto.request.TextCompleteRequest;
import com.chengwenfang.prompt.facade.PromptPackFacade;
import com.chengwenfang.prompt.facade.dto.view.PromptPackPromptBindingView;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 热点文本节点共用：组合 提示词包 + WritingRules 并调用文本模型。
 */
@Component
@RequiredArgsConstructor
public class HotNewsTextCompletionSupport {
  /** 提示词包 组合。 */
  private final PromptPackFacade promptPacks;
  /** 文本 AI。 */
  private final AiTextFacade ai;

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
   * 调用文本补全。
   *
   * @param systemPrompt 系统提示
   * @param userPrompt   用户提示
   * @param scene        审计场景
   * @return 非空正文
   */
  public String complete(String systemPrompt, String userPrompt, String scene) {
    var result = ai.completeText(TextCompleteRequest.builder()
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
