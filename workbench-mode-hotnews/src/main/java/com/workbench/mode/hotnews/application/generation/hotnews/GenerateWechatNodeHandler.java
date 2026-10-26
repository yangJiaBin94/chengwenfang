package com.workbench.mode.hotnews.application.generation.hotnews;

import com.workbench.mode.hotnews.application.prompt.HotNewsWritingPrompts;

import cn.hutool.core.util.StrUtil;
import com.workbench.mode.hotnews.domain.HotNewsMarkdownUtil;
import com.workbench.mode.hotnews.facade.constant.HotNewsArtifactTypes;
import com.workbench.mode.hotnews.facade.enums.HotNewsImageCountOption;
import com.workbench.skill.facade.dto.view.SkillPromptBindingView;
import com.workbench.skill.facade.enums.HotNewsWorkflowNodeType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 将审校后原稿转为公众号成品。
 * <p>先把原稿插图提示词换成占位 token，再改写；提示词本身保留在原稿中供后续生图使用。
 * <p>失败重试时优先复用已成功公众号产物。
 */
@Component
@RequiredArgsConstructor
public class GenerateWechatNodeHandler implements HotNewsNodeHandler {
  /** 文本补全支持。 */
  private final HotNewsTextCompletionSupport textCompletion;
  /** 产物复用。 */
  private final HotNewsArtifactReuseSupport artifactReuse;

  @Override
  public HotNewsWorkflowNodeType supports() {
    return HotNewsWorkflowNodeType.GENERATE_WECHAT;
  }

  @Override
  public void execute(
      HotNewsGraphState state,
      List<SkillPromptBindingView> skillBindings,
      String nodeKey,
      String nodeName) {
    int inlineCount = HotNewsImageCountOption.resolveInlineCount(
        HotNewsImageCountOption.normalize(state.getImageCountOption()));
    String resolvedNodeKey = StrUtil.blankToDefault(nodeKey, supports().getValue());
    Map<String, Object> inputSummary = new LinkedHashMap<>();
    inputSummary.put("draft", HotNewsArtifactReuseSupport.fingerprint(state.getDraft()));
    inputSummary.put("wordCountOption", StrUtil.nullToEmpty(state.getWordCountOption()));

    String reusedReviewed = artifactReuse.tryReuseWechat(
        state.getJobId(),
        HotNewsWorkflowNodeType.REVIEW_WECHAT.getValue(),
        HotNewsArtifactTypes.REVIEWED_WECHAT,
        null);
    if (StrUtil.isNotBlank(reusedReviewed)) {
      state.setWechat(reusedReviewed);
      state.setWechatReviewPassed(true);
      return;
    }
    String reused = artifactReuse.tryReuseWechat(
        state.getJobId(), resolvedNodeKey, HotNewsArtifactTypes.WECHAT, inputSummary);
    if (StrUtil.isBlank(reused)) {
      reused = artifactReuse.tryReuseWechat(
          state.getJobId(), resolvedNodeKey, HotNewsArtifactTypes.WECHAT, null);
    }
    if (StrUtil.isNotBlank(reused)) {
      state.setWechat(reused);
      return;
    }

    // 原稿提示词换成公众号占位，提示词仍留在 draft 供配图节点读取
    HotNewsMarkdownUtil.WechatSource source =
        HotNewsMarkdownUtil.prepareWechatSource(state.getDraft());
    String skillPrompt = textCompletion.composeSkillPrompt(skillBindings);
    String systemPrompt = HotNewsWritingPrompts.systemPrompt(skillPrompt, state.getThemeContext());
    String userPrompt = """
        将下列已审校原稿改写为公众号成品（Markdown）。不新增事实。字数档：%s（插图占位不计字数）
        硬性要求：
        1. 必须原样保留文中的 [[HOT_NEWS_COVER]] / [[HOT_NEWS_IMAGE_N]] 占位，不要删除或改写占位符。
        2. 不要输出“提示词”“插图位置”“插图2”等提示标题，也不要输出 ```text 生图围栏，只保留占位。
        3. 禁止把生图描述写成正文段落（含 Midjourney 参数如 --v 6、Apple Keynote、深海蓝三维科技等视觉提示词）。
        4. 关键概念保持 Markdown 粗体；对话和引用用中文双引号，不使用「」；正文禁用 //、->、——。
        5. 像活人聊天：可有第一人称语气；术语中文优先；禁止词典括注、「它不是 A，是 B」与通稿腔。
        6. 标题与开头突出冲突/变化/影响；段落适合移动端阅读。
        原稿（插图提示词已替换为占位）：
        %s
        """.formatted(
        StrUtil.nullToEmpty(state.getWordCountOption()),
        StrUtil.nullToEmpty(source.prepared()));
    String wechat = textCompletion.complete(
        systemPrompt, userPrompt, HotNewsWorkflowNodeType.GENERATE_WECHAT.getValue());
    wechat = HotNewsMarkdownUtil.ensureWechatTokens(
        wechat, Math.max(inlineCount, source.imageTokens().size()));
    wechat = HotNewsMarkdownUtil.stripWechatProductionResidue(
        HotNewsMarkdownUtil.sanitizeProseSymbols(wechat));
    state.setWechat(wechat);
    artifactReuse.persistTextArtifact(
        state.getJobId(),
        resolvedNodeKey,
        inputSummary,
        HotNewsArtifactTypes.WECHAT,
        wechat,
        Map.of("chars", wechat.length()));
  }
}
