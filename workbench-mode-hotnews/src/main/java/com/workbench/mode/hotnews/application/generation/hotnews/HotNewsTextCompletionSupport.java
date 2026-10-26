package com.workbench.mode.hotnews.application.generation.hotnews;

import cn.hutool.core.util.StrUtil;
import com.workbench.ai.facade.AiTextFacade;
import com.workbench.ai.facade.dto.request.TextCompleteRequest;
import com.workbench.skill.facade.SkillFacade;
import com.workbench.skill.facade.dto.view.SkillPromptBindingView;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 热点文本节点共用：组合 Skill + WritingRules 并调用文本模型。
 */
@Component
@RequiredArgsConstructor
public class HotNewsTextCompletionSupport {
  /** Skill 组合。 */
  private final SkillFacade skills;
  /** 文本 AI。 */
  private final AiTextFacade ai;

  /**
   * 组合绑定 Skill 正文。
   *
   * @param skillBindings 节点绑定
   * @return Skill 提示词
   */
  public String composeSkillPrompt(List<SkillPromptBindingView> skillBindings) {
    return skills.composePromptFromBindings(skillBindings == null ? List.of() : skillBindings);
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
