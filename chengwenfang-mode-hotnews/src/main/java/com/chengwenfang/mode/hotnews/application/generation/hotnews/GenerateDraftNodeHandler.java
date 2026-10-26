package com.chengwenfang.mode.hotnews.application.generation.hotnews;

import com.chengwenfang.mode.hotnews.application.prompt.HotNewsWritingPrompts;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.mode.hotnews.domain.HotNewsMarkdownUtil;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsArtifactTypes;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsArticleSkeletonView;
import com.chengwenfang.mode.hotnews.facade.enums.HotNewsImageCountOption;
import com.chengwenfang.skill.facade.dto.view.SkillPromptBindingView;
import com.chengwenfang.skill.facade.enums.HotNewsWorkflowNodeType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 按文章骨架生成热点原稿。
 * <p>结构以骨架sectionPlan/narrativeShape/architecture 为准；事实包与资料仅作线索。
 * <p>原稿必须包含封面与正文插图提示词块，并保留在正文中（不计字数）。
 * <p>失败重试时优先复用已成功原稿产物。
 */
@Component
@RequiredArgsConstructor
public class GenerateDraftNodeHandler implements HotNewsNodeHandler {
  /** 文本补全支持「*/
  private final HotNewsTextCompletionSupport textCompletion;
  /** 产物复用「*/
  private final HotNewsArtifactReuseSupport artifactReuse;

  @Override
  public HotNewsWorkflowNodeType supports() {
    return HotNewsWorkflowNodeType.GENERATE_DRAFT;
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

    String resolvedNodeKey = StrUtil.blankToDefault(nodeKey, supports().getValue());
    Map<String, Object> inputSummary = new LinkedHashMap<>();
    inputSummary.put("skeleton", HotNewsArtifactReuseSupport.fingerprint(state.getSkeletonJson()));
    inputSummary.put("facts", HotNewsArtifactReuseSupport.fingerprint(state.getFactsJson()));
    inputSummary.put("wordCountOption", StrUtil.nullToEmpty(state.getWordCountOption()));
    inputSummary.put("imageCountOption", imageOption);
    // 优先复用审校后的原稿，避免配图失败重试时再跑生成+审校
    String reusedReviewed = artifactReuse.tryReuseDraft(
        state.getJobId(),
        HotNewsWorkflowNodeType.REVIEW_DRAFT.getValue(),
        HotNewsArtifactTypes.REVIEWED_DRAFT,
        null);
    if (StrUtil.isNotBlank(reusedReviewed)) {
      state.setDraft(reusedReviewed);
      state.setReviewPassed(true);
      return;
    }
    String reused = artifactReuse.tryReuseDraft(
        state.getJobId(), resolvedNodeKey, HotNewsArtifactTypes.DRAFT, inputSummary);
    if (StrUtil.isBlank(reused)) {
      reused = artifactReuse.tryReuseDraft(
          state.getJobId(), resolvedNodeKey, HotNewsArtifactTypes.DRAFT, null);
    }
    if (StrUtil.isNotBlank(reused)) {
      state.setDraft(reused);
      return;
    }

    String skillPrompt = textCompletion.composeSkillPrompt(skillBindings);
    String systemPrompt = HotNewsWritingPrompts.systemPrompt(skillPrompt, state.getThemeContext());
    String userPrompt = """
        请根据「文章骨架」生成完整热点原稿（Markdown）。
        硬性要求：
        1. 结构必须跟随骨架的 narrativeShape、sectionPlan、architecture，不要另起一套大纲。
        2. 覆盖 coreEvent、keyInsights、impacts、readerSignals、coreThesis。
        3. 事实包与参考资料只作线索，禁止逐条复述或编造素材外事实。
        4. 正文字数落在字数档 %s 内（允许少量浮动）；插图提示词不计入字数。
        5. 必须写入插图提示词并保留在原稿中：封面 1 个 + 正文插图 %d 个。
           唯一权威格式：先写「**插图位置：…**」行，再紧跟 ```text 围栏；禁止括号/裸提示词行/引用块/正文段落式生图描述。
           封面标记须含「封面」；正文插图标记写「插图 N｜主题」。
           封面提示词须写出「一眼能看懂的核心内容画面」：围绕骨架 coreThesis / coverBrief：
           用具体可视元素表达冲突或关键数字，让人想点开；禁止抽象氛围空装饰。
        6. 像活人聊天：第一人称可有判断与语气；句子长短参差；禁止训诫小标题，也禁止说明书括注与翻译腔。
        7. 每条插图提示词必须遵循骨架的 illustrationStyle、coverBrief、imagePromptRequirements：
           把生图要素写进提示词正文，不要另起一套画面规范。
        8. 只输出可发布正文与插图提示词块，不要提纲、建议或内部说明。
        标题：%s
        选题摘要：%s
        插图风格：%s
        封面构图要点：%s
        生图硬性要求（须写入每条提示词）：
        %s
        文章骨架 JSON：
        %s
        事实包（线索）：
        %s
        参考资料（线索）：
        %s
        """.formatted(
        StrUtil.nullToEmpty(state.getWordCountOption()),
        inlineCount,
        StrUtil.nullToEmpty(state.getTitle()),
        StrUtil.nullToEmpty(state.getTopicSummary()),
        illustrationStyle,
        coverBrief,
        imagePromptRequirements,
        StrUtil.nullToEmpty(state.getSkeletonJson()),
        StrUtil.nullToEmpty(state.getFactsJson()),
        StrUtil.nullToEmpty(state.getReferencesJson()));
    // 生成原稿，并确定性补齐/裁剪插图提示词数量
    String draft = textCompletion.complete(
        systemPrompt, userPrompt, HotNewsWorkflowNodeType.GENERATE_DRAFT.getValue());
    draft = HotNewsMarkdownUtil.ensureImagePrompts(
        draft,
        inlineCount,
        state.getTitle(),
        illustrationStyle,
        coverBrief,
        imagePromptRequirements);
    state.setDraft(draft);
    artifactReuse.persistTextArtifact(
        state.getJobId(),
        resolvedNodeKey,
        inputSummary,
        HotNewsArtifactTypes.DRAFT,
        draft,
        Map.of("chars", draft.length()));
  }
}
