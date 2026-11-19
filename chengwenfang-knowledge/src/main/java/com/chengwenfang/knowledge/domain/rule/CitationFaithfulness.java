package com.chengwenfang.knowledge.domain.rule;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.knowledge.domain.model.EvidenceSnippet;
import com.chengwenfang.knowledge.facade.constant.KnowledgeRagConstants;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 答案与引用 span/snippet 的粗粒度忠实度检查（P1 门禁用）。
 */
public final class CitationFaithfulness {

  /** 声明级引用标记，如 [1]。 */
  private static final Pattern CITATION_MARKER = Pattern.compile("\\[\\d+\\]");

  private CitationFaithfulness() {}

  /**
   * 判断答案是否至少有一段实质内容可回溯到引用（标记 / 标题 / snippet 重叠）。
   *
   * @param answer    答案
   * @param evidences 证据摘录
   * @return 通过时 true；拒答句视为通过
   */
  public static boolean hasGroundedSpan(String answer, List<EvidenceSnippet> evidences) {
    if (StrUtil.isBlank(answer)) {
      return false;
    }
    String normalizedAnswer = answer.trim();
    if (normalizedAnswer.contains(KnowledgeRagConstants.ABSTAIN_MESSAGE)) {
      return true;
    }
    if (CollUtil.isEmpty(evidences)) {
      return false;
    }
    // 系统提示要求注明编号；有 [n] 即视为声明级引用已落地
    if (CITATION_MARKER.matcher(normalizedAnswer).find()) {
      return true;
    }
    for (EvidenceSnippet evidence : evidences) {
      if (evidence == null) {
        continue;
      }
      if (StrUtil.isNotBlank(evidence.getTitle())
          && normalizedAnswer.contains(evidence.getTitle().trim())) {
        return true;
      }
      String snippet = StrUtil.nullToEmpty(evidence.getSnippet()).trim();
      if (snippet.length() >= 8 && snippetOverlapsAnswer(normalizedAnswer, snippet)) {
        return true;
      }
    }
    return false;
  }

  /**
   * 答案与 snippet 是否有足够长的公共子串。
   *
   * @param answer  答案
   * @param snippet 摘录
   * @return 重叠
   */
  private static boolean snippetOverlapsAnswer(String answer, String snippet) {
    String compactAnswer = answer.replaceAll("\\s+", "");
    String compactSnippet = snippet.replaceAll("\\s+", "");
    if (compactAnswer.contains(compactSnippet) || compactSnippet.contains(compactAnswer)) {
      return true;
    }
    int window = 8;
    if (compactSnippet.length() < window) {
      return compactAnswer.contains(compactSnippet);
    }
    for (int index = 0; index + window <= compactSnippet.length(); index++) {
      if (compactAnswer.contains(compactSnippet.substring(index, index + window))) {
        return true;
      }
    }
    return false;
  }
}
