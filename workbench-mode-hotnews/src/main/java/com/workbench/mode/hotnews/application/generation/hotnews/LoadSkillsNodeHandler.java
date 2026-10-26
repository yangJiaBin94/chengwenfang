package com.workbench.mode.hotnews.application.generation.hotnews;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.workbench.common.BusinessException;
import com.workbench.mode.hotnews.facade.constant.HotNewsErrorCodes;
import com.workbench.skill.facade.dto.view.SkillPromptBindingView;
import com.workbench.skill.facade.enums.HotNewsWorkflowNodeType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 加载并固化本流程 Skill 快照摘要「*/
@Component
@RequiredArgsConstructor
public class LoadSkillsNodeHandler implements HotNewsNodeHandler {
  /** 文本/Skill 组合「*/
  private final HotNewsTextCompletionSupport textCompletion;

  @Override
  public HotNewsWorkflowNodeType supports() {
    return HotNewsWorkflowNodeType.LOAD_SKILLS;
  }

  @Override
  public void execute(
      HotNewsGraphState state,
      List<SkillPromptBindingView> skillBindings,
      String nodeKey,
      String nodeName) {
    if (CollUtil.isEmpty(skillBindings)) {
      throw new BusinessException(
          HotNewsErrorCodes.SKILL_SNAPSHOT_REQUIRED,
          "热点 Skill 绑定为空，无法继续生成",
          422);
    }
    // 组合 Skill 正文，确认可解析
    String skillPrompt = textCompletion.composeSkillPrompt(skillBindings);
    if (StrUtil.isBlank(skillPrompt)) {
      throw new BusinessException(
          HotNewsErrorCodes.SKILL_SNAPSHOT_REQUIRED,
          "热点 Skill 内容为空，无法继续生成",
          422);
    }
    int enabledCount = 0;
    for (SkillPromptBindingView binding : skillBindings) {
      if (binding != null && !Boolean.FALSE.equals(binding.getEnabled())) {
        enabledCount++;
      }
    }
    state.setSkillSnapshotSummary(
        "已加载热点 Skill " + enabledCount + " 个，摘要长度 " + skillPrompt.length());
  }
}
