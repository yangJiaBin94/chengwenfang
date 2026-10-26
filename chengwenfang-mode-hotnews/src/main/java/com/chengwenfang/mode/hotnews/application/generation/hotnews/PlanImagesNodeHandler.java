package com.chengwenfang.mode.hotnews.application.generation.hotnews;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.mode.hotnews.domain.HotNewsMarkdownUtil;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsArticleSkeletonView;
import com.chengwenfang.mode.hotnews.facade.enums.HotNewsImageCountOption;
import com.chengwenfang.skill.facade.dto.view.SkillPromptBindingView;
import com.chengwenfang.skill.facade.enums.HotNewsWorkflowNodeType;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 按原稿插图提示词与插图档确认配图计划（确定性节点）。
 * <p>封面必出；正文插图至少 2 张。优先使用原稿中已保留的提示词。
 */
@Component
public class PlanImagesNodeHandler implements HotNewsNodeHandler {

  @Override
  public HotNewsWorkflowNodeType supports() {
    return HotNewsWorkflowNodeType.PLAN_IMAGES;
  }

  @Override
  public void execute(
      HotNewsGraphState state,
      List<SkillPromptBindingView> skillBindings,
      String nodeKey,
      String nodeName) {
    String imageOption = HotNewsImageCountOption.normalize(state.getImageCountOption());
    state.setImageCountOption(imageOption);
    int inlineCount = HotNewsImageCountOption.resolveInlineCount(imageOption);
    HotNewsArticleSkeletonView skeleton = state.getSkeleton();
    String illustrationStyle = skeleton == null
        ? HotNewsMarkdownUtil.DEFAULT_ILLUSTRATION_STYLE
        : StrUtil.blankToDefault(
            skeleton.getIllustrationStyle(), HotNewsMarkdownUtil.DEFAULT_ILLUSTRATION_STYLE);
    String coverBrief = skeleton == null
        ? StrUtil.nullToEmpty(state.getTitle())
        : StrUtil.blankToDefault(skeleton.getCoverBrief(), state.getTitle());
    String imagePromptRequirements = skeleton == null
        ? HotNewsMarkdownUtil.defaultImagePromptRequirements()
        : StrUtil.blankToDefault(
            skeleton.getImagePromptRequirements(),
            HotNewsMarkdownUtil.defaultImagePromptRequirements());
    String draft = StrUtil.nullToEmpty(state.getDraft());
    // 若审校后提示词不足，先按骨架要求补齐再规划
    draft = HotNewsMarkdownUtil.ensureImagePrompts(
        draft,
        inlineCount,
        state.getTitle(),
        illustrationStyle,
        coverBrief,
        imagePromptRequirements);
    state.setDraft(draft);

    String coverPrompt = HotNewsMarkdownUtil.coverPrompt(draft);
    List<HotNewsMarkdownUtil.PromptHit> inlinePrompts = HotNewsMarkdownUtil.findInlinePrompts(draft);
    StringBuilder plan = new StringBuilder();
    plan.append("配图计划（来自原稿插图提示词）\n");
    plan.append("- 插图档：").append(imageOption)
        .append("（正文插图 ").append(inlineCount).append(" 张 + 封面 1 张，封面必出）\n");
    plan.append("- 风格：").append(illustrationStyle).append('\n');
    plan.append("- 生图要求（来自骨架）：").append(imagePromptRequirements).append('\n');
    plan.append("- 封面（").append(HotNewsImageSizes.COVER_SIZE).append("）：")
        .append(StrUtil.blankToDefault(coverPrompt, coverBrief)).append('\n');
    for (int sequence = 1; sequence <= inlineCount; sequence++) {
      String promptText = sequence <= inlinePrompts.size()
          ? inlinePrompts.get(sequence - 1).prompt()
          : "围绕标题「" + StrUtil.nullToEmpty(state.getTitle()) + "」的具体判断";
      plan.append("- 正文图").append(sequence)
          .append("（").append(HotNewsImageSizes.INLINE_SIZE).append("）：")
          .append(promptText.replace('\n', ' '))
          .append("；占位 ")
          .append(HotNewsImageSizes.inlineToken(sequence))
          .append('\n');
    }
    state.setImagePlan(plan.toString());
    state.setImagesSkipped(false);
  }
}
