package com.workbench.mode.hotnews.application.generation.hotnews;

import com.workbench.mode.hotnews.application.prompt.HotNewsWritingPrompts;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.workbench.common.BusinessException;
import com.workbench.mode.hotnews.facade.constant.HotNewsArtifactTypes;
import com.workbench.mode.hotnews.facade.constant.HotNewsErrorCodes;
import com.workbench.skill.facade.dto.view.SkillPromptBindingView;
import com.workbench.skill.facade.enums.HotNewsWorkflowNodeType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 整理资料线索为事实包，供写作守边界。
 * <p>不替代文章骨架结构；骨架已有的coreEvent/insights 优先。
 * <p>失败重试时优先复用已成功事实包产物。
 */
@Component
@RequiredArgsConstructor
public class PrepareFactsNodeHandler implements HotNewsNodeHandler {
  /** 文本补全支持「*/
  private final HotNewsTextCompletionSupport textCompletion;
  /** 产物复用「*/
  private final HotNewsArtifactReuseSupport artifactReuse;

  @Override
  public HotNewsWorkflowNodeType supports() {
    return HotNewsWorkflowNodeType.PREPARE_FACTS;
  }

  @Override
  public void execute(
      HotNewsGraphState state,
      List<SkillPromptBindingView> skillBindings,
      String nodeKey,
      String nodeName) {
    if (StrUtil.isBlank(state.getSkeletonJson())) {
      throw new BusinessException(
          HotNewsErrorCodes.ARTICLE_SKELETON_REQUIRED,
          "缺少文章骨架，无法整理事实包",
          422);
    }
    String resolvedNodeKey = StrUtil.blankToDefault(nodeKey, supports().getValue());
    Map<String, Object> inputSummary = new LinkedHashMap<>();
    inputSummary.put("skeleton", HotNewsArtifactReuseSupport.fingerprint(state.getSkeletonJson()));
    inputSummary.put("references", HotNewsArtifactReuseSupport.fingerprint(state.getReferencesJson()));
    inputSummary.put("title", StrUtil.nullToEmpty(state.getTitle()));
    String reused = artifactReuse.tryReuseFacts(state.getJobId(), resolvedNodeKey, inputSummary);
    if (StrUtil.isBlank(reused)) {
      reused = artifactReuse.tryReuseFacts(state.getJobId(), resolvedNodeKey, null);
    }
    if (StrUtil.isNotBlank(reused)) {
      state.setFactsJson(reused);
      return;
    }
    String skillPrompt = textCompletion.composeSkillPrompt(skillBindings);
    String systemPrompt = HotNewsWritingPrompts.systemPrompt(skillPrompt, state.getThemeContext());
    String userPrompt = """
        根据文章骨架与参考资料，整理「已确认可用事实包」（JSON）。
        要求：
        1. 以骨架coreEvent/keyInsights/impacts 为轴，资料只作补充线索。
        2. 字段建议：coreEvent、timeline、entities、numbers、impacts、boundaries、assumptions、sourceHints。
        3. 不要输出可信度分数、冲突标签或审核术语，不要重写文章大纲。
        4. 只输出 JSON。
        标题：%s
        骨架 JSON：
        %s
        参考资料：
        %s
        """.formatted(
        StrUtil.nullToEmpty(state.getTitle()),
        StrUtil.nullToEmpty(state.getSkeletonJson()),
        StrUtil.nullToEmpty(state.getReferencesJson()));
    String facts = textCompletion.complete(
        systemPrompt, userPrompt, HotNewsWorkflowNodeType.PREPARE_FACTS.getValue());
    if (!JSONUtil.isTypeJSON(facts)) {
      Map<String, Object> fallback = new LinkedHashMap<>();
      fallback.put("rawFacts", facts);
      fallback.put("skeletonJson", state.getSkeletonJson());
      fallback.put("referencesJson", state.getReferencesJson());
      facts = JSONUtil.toJsonStr(fallback);
    }
    state.setFactsJson(facts);
    artifactReuse.persistTextArtifact(
        state.getJobId(),
        resolvedNodeKey,
        inputSummary,
        HotNewsArtifactTypes.FACTS,
        facts,
        Map.of("chars", facts.length()));
  }
}
