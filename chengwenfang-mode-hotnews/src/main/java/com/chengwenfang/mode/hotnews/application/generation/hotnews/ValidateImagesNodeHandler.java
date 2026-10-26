package com.chengwenfang.mode.hotnews.application.generation.hotnews;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.mode.hotnews.domain.HotNewsMarkdownUtil;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsErrorCodes;
import com.chengwenfang.mode.hotnews.facade.enums.HotNewsImageCountOption;
import com.chengwenfang.skill.facade.dto.view.SkillPromptBindingView;
import com.chengwenfang.skill.facade.enums.HotNewsWorkflowNodeType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 校验配图数量、占位替换与提示词泄漏。
 */
@Component
@RequiredArgsConstructor
public class ValidateImagesNodeHandler implements HotNewsNodeHandler {
  @Override
  public HotNewsWorkflowNodeType supports() {
    return HotNewsWorkflowNodeType.VALIDATE_IMAGES;
  }

  @Override
  public void execute(
      HotNewsGraphState state,
      List<SkillPromptBindingView> skillBindings,
      String nodeKey,
      String nodeName) {
    if (Boolean.TRUE.equals(state.getImagesSkipped())) {
      state.setImagesValidated(true);
      return;
    }
    String wechat = StrUtil.nullToEmpty(state.getWechat());
    int expectedInline = Math.max(
        HotNewsImageSizes.mapInlineCount(state.getImageCountOption()),
        HotNewsImageCountOption.minimumInlineCount());
    List<HotNewsGeneratedImageSummary> generated =
        state.getGeneratedImages() == null ? List.of() : state.getGeneratedImages();
    long coverCount = generated.stream()
        .filter(item -> HotNewsImageKind.COVER.getValue().equals(item.getKind()))
        .count();
    long inlineCount = generated.stream()
        .filter(item -> HotNewsImageKind.INLINE.getValue().equals(item.getKind()))
        .count();
    if (coverCount < 1) {
      fail("缺少封面图");
    }
    if (inlineCount < expectedInline) {
      fail("正文插图数量不足：期望 " + expectedInline + "，实际 " + inlineCount);
    }
    if (wechat.contains(HotNewsImageSizes.COVER_TOKEN)) {
      fail("公众号正文仍保留封面占位 " + HotNewsImageSizes.COVER_TOKEN);
    }
    for (int sequence = 1; sequence <= expectedInline; sequence++) {
      String token = HotNewsImageSizes.inlineToken(sequence);
      if (wechat.contains(token)) {
        fail("公众号正文仍保留插图占位 " + token);
      }
    }
    if (CollUtil.isEmpty(generated)) {
      fail("未生成任何配图记录");
    }
    if (HotNewsMarkdownUtil.hasPromptLeak(wechat)) {
      fail("公众号正文仍残留插图提示词");
    }
    state.setImagesValidated(true);
  }

  /**
   * 抛出配图校验失败。
   *
   * @param message 原因
   */
  private static void fail(String message) {
    throw new BusinessException(HotNewsErrorCodes.IMAGE_VALIDATION_FAILED, message, 422);
  }
}
