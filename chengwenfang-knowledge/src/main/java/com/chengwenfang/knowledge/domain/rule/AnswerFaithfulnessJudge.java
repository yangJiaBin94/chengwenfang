package com.chengwenfang.knowledge.domain.rule;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.knowledge.domain.model.EvidenceSnippet;
import com.chengwenfang.knowledge.facade.constant.KnowledgeRagConstants;
import java.util.List;

/**
 * 解析 LLM 忠实度校验结果；规则侧快速拒绝。
 */
public final class AnswerFaithfulnessJudge {

  private AnswerFaithfulnessJudge() {}

  /**
   * 是否应跳过 LLM 校验（拒答句或空）。
   *
   * @param answer 答案
   * @return 跳过
   */
  public static boolean shouldSkip(String answer) {
    String text = StrUtil.nullToEmpty(answer).trim();
    return StrUtil.isBlank(text) || text.contains(KnowledgeRagConstants.ABSTAIN_MESSAGE);
  }

  /**
   * 组装校验用用户提示。
   *
   * @param question  问题
   * @param answer    答案
   * @param evidences 证据摘录
   * @return 提示
   */
  public static String buildJudgePrompt(
      String question, String answer, List<EvidenceSnippet> evidences) {
    StringBuilder builder = new StringBuilder();
    builder.append("问题：\n").append(StrUtil.nullToEmpty(question)).append("\n\n");
    builder.append("答案：\n").append(StrUtil.nullToEmpty(answer)).append("\n\n");
    builder.append("参考摘录：\n");
    int index = 1;
    if (evidences != null) {
      for (EvidenceSnippet evidence : evidences) {
        builder.append('[').append(index++).append("] ")
            .append(StrUtil.blankToDefault(evidence == null ? null : evidence.getSnippet(), ""))
            .append('\n');
      }
    }
    builder.append("\n只回答 YES 或 NO：答案是否在回答该问题，且关键事实能被摘录支持？");
    return builder.toString();
  }

  /**
   * 解析模型输出是否通过。
   *
   * @param modelText 模型原文
   * @return 通过
   */
  public static boolean passes(String modelText) {
    String text = StrUtil.nullToEmpty(modelText).trim().toUpperCase();
    if (text.startsWith("NO") || text.contains("\nNO") || text.equals("N")) {
      return false;
    }
    if (text.startsWith("YES") || text.contains("\nYES") || text.equals("Y")) {
      return true;
    }
    // 无法解析时不误杀，交回表面忠实度
    return true;
  }
}
