package com.chengwenfang.mode.hotnews.application.generation.hotnews;

import com.chengwenfang.mode.hotnews.application.prompt.HotNewsWritingPrompts;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.mode.hotnews.domain.HotNewsMarkdownUtil;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsArtifactTypes;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsErrorCodes;
import com.chengwenfang.mode.hotnews.facade.enums.HotNewsImageCountOption;
import com.chengwenfang.skill.facade.dto.view.SkillPromptBindingView;
import com.chengwenfang.skill.facade.enums.HotNewsWorkflowNodeType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 审校公众号成品：保真、导语、移动端表达与内部术语清理。
 * <p>失败重试时优先复用已审校公众号正文。
 */
@Component
@RequiredArgsConstructor
public class ReviewWechatNodeHandler implements HotNewsNodeHandler {
  /** 文本补全支持「*/
  private final HotNewsTextCompletionSupport textCompletion;
  /** 产物复用「*/
  private final HotNewsArtifactReuseSupport artifactReuse;

  @Override
  public HotNewsWorkflowNodeType supports() {
    return HotNewsWorkflowNodeType.REVIEW_WECHAT;
  }

  @Override
  public void execute(
      HotNewsGraphState state,
      List<SkillPromptBindingView> skillBindings,
      String nodeKey,
      String nodeName) {
    if (StrUtil.isBlank(state.getWechat()) || StrUtil.isBlank(state.getDraft())) {
      throw new BusinessException(
          HotNewsErrorCodes.WECHAT_REVIEW_FAILED,
          "公众号正文或原稿为空，无法审校",
          422);
    }
    String resolvedNodeKey = StrUtil.blankToDefault(nodeKey, supports().getValue());
    Map<String, Object> inputSummary = new LinkedHashMap<>();
    inputSummary.put("wechat", HotNewsArtifactReuseSupport.fingerprint(state.getWechat()));
    inputSummary.put("draft", HotNewsArtifactReuseSupport.fingerprint(state.getDraft()));
    inputSummary.put("wordCountOption", StrUtil.nullToEmpty(state.getWordCountOption()));

    String reused = artifactReuse.tryReuseWechat(
        state.getJobId(), resolvedNodeKey, HotNewsArtifactTypes.REVIEWED_WECHAT, inputSummary);
    if (StrUtil.isBlank(reused)) {
      reused = artifactReuse.tryReuseWechat(
          state.getJobId(), resolvedNodeKey, HotNewsArtifactTypes.REVIEWED_WECHAT, null);
    }
    if (StrUtil.isNotBlank(reused)) {
      state.setWechat(reused);
      state.setWechatReviewPassed(true);
      return;
    }
    if (Boolean.TRUE.equals(state.getWechatReviewPassed()) && StrUtil.isNotBlank(state.getWechat())) {
      return;
    }

    String skillPrompt = textCompletion.composeSkillPrompt(skillBindings);
    String systemPrompt = HotNewsWritingPrompts.systemPrompt(skillPrompt, state.getThemeContext());
    int inlineCount = HotNewsImageCountOption.resolveInlineCount(
        HotNewsImageCountOption.normalize(state.getImageCountOption()));
    String userPrompt = """
        审校下列公众号成品。检查：与原稿事实一致、标题导语清晰、适合移动端、无内部审核术语：
        字数落在字数档 %s 内（允许少量浮动；插图占位不计字数）。
        若有问题直接输出修订后的完整公众号正文；若通过则原样输出完整正文。不要只输出检查清单。
        必须保留 [[HOT_NEWS_COVER]] / [[HOT_NEWS_IMAGE_N]] 占位；不得出现“提示词”、提示代码块、“插图位置”或“插图2”等提示标题。
        文风：改到像真人聊天——有口气、有判断，不要说明书括注、「它不是 A，是 B」和通稿腔；
        检查关键概念粗体、中文双引号；清除正文「」。/。>、——。
        原稿（仅供事实对照，勿把插图提示词抄进公众号）：
        %s
        公众号正文：
        %s
        """.formatted(
        StrUtil.nullToEmpty(state.getWordCountOption()),
        StrUtil.nullToEmpty(state.getDraft()),
        StrUtil.nullToEmpty(state.getWechat()));
    String reviewed = textCompletion.complete(
        systemPrompt, userPrompt, HotNewsWorkflowNodeType.REVIEW_WECHAT.getValue());
    reviewed = HotNewsMarkdownUtil.ensureWechatTokens(reviewed, inlineCount);
    reviewed = HotNewsMarkdownUtil.stripWechatProductionResidue(
        HotNewsMarkdownUtil.sanitizeProseSymbols(reviewed));
    boolean passed = !looksBroken(reviewed) && !HotNewsMarkdownUtil.hasPromptLeak(reviewed);
    state.setWechat(reviewed);
    state.setWechatReviewPassed(passed);
    if (!passed) {
      throw new BusinessException(
          HotNewsErrorCodes.WECHAT_REVIEW_FAILED,
          "公众号审校未通过",
          422);
    }
    if (!HotNewsWordCountRules.withinRange(
        reviewed, state.getWordCountOption(), HotNewsWordCountRules.DEFAULT_TOLERANCE)) {
      throw new BusinessException(
          HotNewsErrorCodes.WORD_COUNT_FAILED,
          "公众号正文" + HotNewsWordCountRules.describeMismatch(
              reviewed, state.getWordCountOption()),
          422);
    }
    artifactReuse.persistTextArtifact(
        state.getJobId(),
        resolvedNodeKey,
        inputSummary,
        HotNewsArtifactTypes.REVIEWED_WECHAT,
        reviewed,
        Map.of("chars", reviewed.length(), "wechatReviewPassed", true));
  }

  /**
   * 粗判审校输出是否明显不合格。
   *
   * @param text 审校输出
   * @return 不合格则个true
   */
  private static boolean looksBroken(String text) {
    if (StrUtil.isBlank(text) || text.length() < 80) {
      return true;
    }
    return text.contains("检查清单") && text.length() < 200;
  }
}
