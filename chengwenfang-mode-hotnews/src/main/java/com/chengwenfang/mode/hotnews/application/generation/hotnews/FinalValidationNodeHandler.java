package com.chengwenfang.mode.hotnews.application.generation.hotnews;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.mode.hotnews.domain.HotNewsMarkdownUtil;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsErrorCodes;
import com.chengwenfang.mode.hotnews.facade.enums.HotNewsImageCountOption;
import com.chengwenfang.skill.facade.dto.view.SkillPromptBindingView;
import com.chengwenfang.skill.facade.enums.HotNewsWorkflowNodeType;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 最终完整性检查：骨架、原稿、公众号、审校、配图与字数。
 */
@Component
@RequiredArgsConstructor
public class FinalValidationNodeHandler implements HotNewsNodeHandler {
  @Override
  public HotNewsWorkflowNodeType supports() {
    return HotNewsWorkflowNodeType.FINAL_VALIDATION;
  }

  @Override
  public void execute(
      HotNewsGraphState state,
      List<SkillPromptBindingView> skillBindings,
      String nodeKey,
      String nodeName) {
    List<String> issues = new ArrayList<>();
    if (StrUtil.isBlank(state.getSkeletonJson())) {
      issues.add("缺少文章骨架");
    }
    if (StrUtil.isBlank(state.getDraft())) {
      issues.add("原稿为空");
    }
    if (StrUtil.isBlank(state.getWechat())) {
      issues.add("公众号正文为空");
    }
    if (!Boolean.TRUE.equals(state.getReviewPassed())) {
      issues.add("原稿审校未通过");
    }
    if (!Boolean.TRUE.equals(state.getWechatReviewPassed())) {
      issues.add("公众号审校未通过");
    }
    if (!Boolean.TRUE.equals(state.getImagesSkipped())
        && !Boolean.TRUE.equals(state.getImagesValidated())) {
      issues.add("配图校验未通过");
    }
    if (StrUtil.isBlank(state.getFactsJson())) {
      issues.add("缺少事实包产物");
    }
    if (StrUtil.isNotBlank(state.getDraft())
        && !HotNewsWordCountRules.withinRange(
            state.getDraft(), state.getWordCountOption(), HotNewsWordCountRules.DEFAULT_TOLERANCE)) {
      issues.add("原稿" + HotNewsWordCountRules.describeMismatch(
          state.getDraft(), state.getWordCountOption()));
    }
    if (StrUtil.isNotBlank(state.getWechat())
        && !HotNewsWordCountRules.withinRange(
            state.getWechat(), state.getWordCountOption(), HotNewsWordCountRules.DEFAULT_TOLERANCE)) {
      issues.add("公众号正文" + HotNewsWordCountRules.describeMismatch(
          state.getWechat(), state.getWordCountOption()));
    }
    int expectedInline = Math.max(
        HotNewsImageSizes.mapInlineCount(state.getImageCountOption()),
        HotNewsImageCountOption.minimumInlineCount());
    List<HotNewsGeneratedImageSummary> generatedImages =
        state.getGeneratedImages() == null ? List.of() : state.getGeneratedImages();
    long actualCover = generatedImages.stream()
        .filter(item -> HotNewsImageKind.COVER.getValue().equals(item.getKind()))
        .count();
    long actualInline = generatedImages.stream()
        .filter(item -> HotNewsImageKind.INLINE.getValue().equals(item.getKind()))
        .count();
    if (actualCover < 1) {
      issues.add("缺少封面图");
    }
    if (actualInline < expectedInline) {
      issues.add("正文插图数量不足：期望 " + expectedInline + "，实际 " + actualInline);
    }
    if (StrUtil.isBlank(HotNewsMarkdownUtil.coverPrompt(state.getDraft()))) {
      issues.add("原稿缺少封面插图提示词");
    }
    int draftInlinePrompts = HotNewsMarkdownUtil.findInlinePrompts(state.getDraft()).size();
    if (draftInlinePrompts < expectedInline) {
      issues.add("原稿正文插图提示词不足：期望 " + expectedInline + "，实际 " + draftInlinePrompts);
    }
    if (!HotNewsMarkdownUtil.draftImagesFollowPrompts(state.getDraft())) {
      issues.add("原稿图片引用必须紧跟对应插图提示词");
    }
    if (HotNewsMarkdownUtil.hasPromptLeak(state.getWechat())) {
      issues.add("公众号正文残留提示词、提示代码块或插图提示标题");
    }
    if (!issues.isEmpty()) {
      throw new BusinessException(
          HotNewsErrorCodes.FINAL_VALIDATION_FAILED,
          "最终完整性检查未通过：" + String.join("；", issues),
          422);
    }
    state.setFinalValidationPassed(true);
  }
}
