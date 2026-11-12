package com.chengwenfang.mode.hotnews.application.generation.hotnews;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsErrorCodes;
import com.chengwenfang.prompt.facade.dto.view.PromptPackPromptBindingView;
import com.chengwenfang.prompt.facade.enums.HotNewsWorkflowNodeType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 加载并固化本流程 提示词包 快照摘要「*/
@Component
@RequiredArgsConstructor
public class LoadPromptPacksNodeHandler implements HotNewsNodeHandler {
  /** 文本/提示词包 组合「*/
  private final HotNewsTextCompletionSupport textCompletion;

  @Override
  public HotNewsWorkflowNodeType supports() {
    return HotNewsWorkflowNodeType.LOAD_PROMPT_PACKS;
  }

  @Override
  public void execute(
      HotNewsGraphState state,
      List<PromptPackPromptBindingView> promptPackBindings,
      String nodeKey,
      String nodeName) {
    if (CollUtil.isEmpty(promptPackBindings)) {
      throw new BusinessException(
          HotNewsErrorCodes.PROMPT_PACK_SNAPSHOT_REQUIRED,
          "热点 提示词包绑定为空，无法继续生成",
          422);
    }
    // 组合 提示词包 正文，确认可解析
    String promptPackText = textCompletion.composePromptPackText(promptPackBindings);
    if (StrUtil.isBlank(promptPackText)) {
      throw new BusinessException(
          HotNewsErrorCodes.PROMPT_PACK_SNAPSHOT_REQUIRED,
          "热点 提示词包 内容为空，无法继续生成",
          422);
    }
    int enabledCount = 0;
    for (PromptPackPromptBindingView binding : promptPackBindings) {
      if (binding != null && !Boolean.FALSE.equals(binding.getEnabled())) {
        enabledCount++;
      }
    }
    state.setPromptPackSnapshotSummary(
        "已加载热点 提示词包 " + enabledCount + " 个，摘要长度 " + promptPackText.length());
  }
}
