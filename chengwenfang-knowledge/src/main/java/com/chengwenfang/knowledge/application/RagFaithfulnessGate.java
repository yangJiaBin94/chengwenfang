package com.chengwenfang.knowledge.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.ai.facade.AiVisionFacade;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.dto.request.VisionCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.VisionCompleteResult;
import com.chengwenfang.infra.config.ChengwenfangProperties;
import com.chengwenfang.knowledge.domain.model.EvidenceSnippet;
import com.chengwenfang.knowledge.domain.rule.AnswerFaithfulnessJudge;
import com.chengwenfang.knowledge.domain.rule.CitationFaithfulness;
import com.chengwenfang.knowledge.facade.constant.KnowledgeToolNames;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeCitationView;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 答案忠实度门禁：表面 span 校验 + 可选 LLM 二次校验。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagFaithfulnessGate {

  /** 配置。 */
  private final ChengwenfangProperties properties;
  /** 多模态补全。 */
  private final AiVisionFacade aiVisionFacade;
  /** Prompt 版本。 */
  private final KnowledgePromptRegistry promptRegistry;

  /**
   * 表面 + LLM 忠实度是否通过；配置关闭时一律视为通过。
   *
   * @param question  问题
   * @param answer    答案
   * @param citations 引用
   * @param toolSteps 步骤记录（可空）
   * @return 通过
   */
  public boolean passes(
      String question,
      String answer,
      List<KnowledgeCitationView> citations,
      KnowledgeToolStepRecorder toolSteps) {
    boolean enabled = properties.getKnowledge() == null
        || properties.getKnowledge().getFaithfulness() == null
        || properties.getKnowledge().getFaithfulness().isEnabled();
    if (!enabled || AnswerFaithfulnessJudge.shouldSkip(answer)) {
      return true;
    }
    List<EvidenceSnippet> evidences = toEvidences(citations);
    if (!CitationFaithfulness.hasGroundedSpan(answer, evidences)) {
      if (toolSteps != null) {
        toolSteps.append(KnowledgeToolNames.FAITHFULNESS, "surface", "fail", 0L, false);
      }
      return false;
    }
    long started = System.currentTimeMillis();
    try {
      VisionCompleteResult result = aiVisionFacade.complete(VisionCompleteRequest.builder()
          .systemPrompt(promptRegistry.systemTemplate(ModelCallScenes.KNOWLEDGE_ANSWER_FAITHFULNESS))
          .prompt(AnswerFaithfulnessJudge.buildJudgePrompt(question, answer, evidences))
          .scene(ModelCallScenes.KNOWLEDGE_ANSWER_FAITHFULNESS)
          .build());
      boolean passed = AnswerFaithfulnessJudge.passes(result == null ? "" : result.getText());
      if (toolSteps != null) {
        toolSteps.append(
            KnowledgeToolNames.FAITHFULNESS,
            "llm",
            passed ? "pass" : "fail",
            System.currentTimeMillis() - started,
            passed);
      }
      return passed;
    } catch (Exception exception) {
      log.warn("忠实度校验失败，回退表面门禁：{}", exception.getMessage());
      if (toolSteps != null) {
        // fail-open 仍放行答案，但步骤记 ok=false，便于观测异常
        toolSteps.append(
            KnowledgeToolNames.FAITHFULNESS,
            "llm",
            "error_fail_open",
            System.currentTimeMillis() - started,
            false);
      }
      return true;
    }
  }

  /**
   * CitationView → 领域证据摘录。
   *
   * @param citations 引用
   * @return 证据
   */
  public static List<EvidenceSnippet> toEvidences(List<KnowledgeCitationView> citations) {
    if (CollUtil.isEmpty(citations)) {
      return List.of();
    }
    List<EvidenceSnippet> evidences = new ArrayList<>();
    for (KnowledgeCitationView citation : citations) {
      if (citation == null) {
        continue;
      }
      evidences.add(EvidenceSnippet.builder()
          .title(citation.getTitle())
          .snippet(citation.getSnippet())
          .build());
    }
    return evidences;
  }

  /**
   * 仅表面 span 是否通过（生成后快速门）。
   *
   * @param answer    答案
   * @param citations 引用
   * @return 通过
   */
  public boolean hasGroundedSpan(String answer, List<KnowledgeCitationView> citations) {
    return CitationFaithfulness.hasGroundedSpan(answer, toEvidences(citations));
  }
}
