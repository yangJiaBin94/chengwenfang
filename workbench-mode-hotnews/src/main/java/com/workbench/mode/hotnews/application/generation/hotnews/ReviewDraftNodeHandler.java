package com.workbench.mode.hotnews.application.generation.hotnews;

import com.workbench.mode.hotnews.application.prompt.HotNewsWritingPrompts;

import cn.hutool.core.util.StrUtil;
import com.workbench.common.BusinessException;
import com.workbench.mode.hotnews.domain.HotNewsMarkdownUtil;
import com.workbench.mode.hotnews.facade.constant.HotNewsArtifactTypes;
import com.workbench.mode.hotnews.facade.constant.HotNewsErrorCodes;
import com.workbench.mode.hotnews.facade.dto.view.HotNewsArticleSkeletonView;
import com.workbench.mode.hotnews.facade.enums.HotNewsImageCountOption;
import com.workbench.skill.facade.dto.view.SkillPromptBindingView;
import com.workbench.skill.facade.enums.HotNewsWorkflowNodeType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 审校热点原稿：结构跟骨架、事实边界、文风与字数档。
 * <p>失败重试时优先复用已审校原稿。
 */
@Component
@RequiredArgsConstructor
public class ReviewDraftNodeHandler implements HotNewsNodeHandler {
  /** 文本补全支持「*/
  private final HotNewsTextCompletionSupport textCompletion;
  /** 产物复用「*/
  private final HotNewsArtifactReuseSupport artifactReuse;

  @Override
  public HotNewsWorkflowNodeType supports() {
    return HotNewsWorkflowNodeType.REVIEW_DRAFT;
  }

  @Override
  public void execute(
      HotNewsGraphState state,
      List<SkillPromptBindingView> skillBindings,
      String nodeKey,
      String nodeName) {
    String imageOption = HotNewsImageCountOption.normalize(state.getImageCountOption());
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

    String resolvedNodeKey = StrUtil.blankToDefault(nodeKey, supports().getValue());
    Map<String, Object> inputSummary = new LinkedHashMap<>();
    inputSummary.put("draft", HotNewsArtifactReuseSupport.fingerprint(state.getDraft()));
    inputSummary.put("wordCountOption", StrUtil.nullToEmpty(state.getWordCountOption()));
    String reused = artifactReuse.tryReuseDraft(
        state.getJobId(), resolvedNodeKey, HotNewsArtifactTypes.REVIEWED_DRAFT, inputSummary);
    if (StrUtil.isBlank(reused)) {
      reused = artifactReuse.tryReuseDraft(
          state.getJobId(), resolvedNodeKey, HotNewsArtifactTypes.REVIEWED_DRAFT, null);
    }
    if (StrUtil.isNotBlank(reused)) {
      state.setDraft(reused);
      state.setReviewPassed(true);
      return;
    }
    // 上游 GenerateDraft 已灌入审校稿时直接放行
    if (Boolean.TRUE.equals(state.getReviewPassed()) && StrUtil.isNotBlank(state.getDraft())) {
      return;
    }

    String skillPrompt = textCompletion.composeSkillPrompt(skillBindings);
    String systemPrompt = HotNewsWritingPrompts.systemPrompt(skillPrompt, state.getThemeContext());
    String userPrompt = """
        审校下列热点原稿。若有流水账、AI 套话、“一、二、三”式大标题、教学式强势语气、偏离骨架结构。
        词典括注/翻译腔，或字数明显偏离，直接输出修订后的完整正文；若通过则原样输出完整正文。不要只输出检查清单。
        语气与文风（改到像真人聊天，不像通稿或课件）：
        1. 删改训诫口吻；可读起来有第一人称判断，句子长短自然，不要播音腔或万能收尾套话。
        2. 改掉「英文词（长串中文释义）」式括注；术语中文优先，必要时括号只留短英文缩写；意思嵌进句子里说。
        3. 改掉「它不是 A，是 B」「更直观的是」「值得注意的是」等机械套式与直译感句子。
        必须完整保留原稿中的插图提示词块（“插图位置… ```text 围栏）；不得删除或改写成占位符。
        检查关键概念粗体、中文双引号；清除正文「」。/。>、——（提示词围栏内不受限）。
        正文字数档：%s（插图提示词不计入字数）。
        骨架 JSON：
        %s
        事实包：
        %s
        原稿：
        %s
        """.formatted(
        StrUtil.nullToEmpty(state.getWordCountOption()),
        StrUtil.nullToEmpty(state.getSkeletonJson()),
        StrUtil.nullToEmpty(state.getFactsJson()),
        StrUtil.nullToEmpty(state.getDraft()));
    String reviewed = textCompletion.complete(
        systemPrompt, userPrompt, HotNewsWorkflowNodeType.REVIEW_DRAFT.getValue());
    // 审校后仍补齐插图提示词，避免模型误删
    reviewed = HotNewsMarkdownUtil.ensureImagePrompts(
        reviewed,
        inlineCount,
        state.getTitle(),
        illustrationStyle,
        coverBrief,
        imagePromptRequirements);
    reviewed = HotNewsMarkdownUtil.sanitizeProseSymbols(reviewed);
    boolean passed = !looksBroken(reviewed);
    state.setDraft(reviewed);
    state.setReviewPassed(passed);
    if (!passed) {
      throw new BusinessException(
          HotNewsErrorCodes.FINAL_VALIDATION_FAILED,
          "热点原稿审校未通过",
          422);
    }
    // 字数硬校验（允许浮动；不计插图提示词：
    if (!HotNewsWordCountRules.withinRange(
        reviewed, state.getWordCountOption(), HotNewsWordCountRules.DEFAULT_TOLERANCE)) {
      throw new BusinessException(
          HotNewsErrorCodes.WORD_COUNT_FAILED,
          "原稿" + HotNewsWordCountRules.describeMismatch(reviewed, state.getWordCountOption()),
          422);
    }
    artifactReuse.persistTextArtifact(
        state.getJobId(),
        resolvedNodeKey,
        inputSummary,
        HotNewsArtifactTypes.REVIEWED_DRAFT,
        reviewed,
        Map.of("chars", reviewed.length(), "reviewPassed", true));
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
