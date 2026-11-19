package com.chengwenfang.knowledge.application;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.infra.config.ChengwenfangProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 知识库 Context Budget：四段配额切片（字符启发式 token ≈ chars/4）。
 */
@Component
@RequiredArgsConstructor
public class KnowledgeContextBudget {

  /** 配置。 */
  private final ChengwenfangProperties properties;

  /**
   * 切片结果。
   *
   * @param summary   摘要段
   * @param recent    最近对话段
   * @param memory    记忆段
   * @param citations 引用段（已编号正文）
   * @param truncated 被截断的段名
   */
  public record SliceResult(
      String summary,
      String recent,
      String memory,
      String citations,
      List<String> truncated) {}

  /**
   * 按配额切片四段文本。
   *
   * @param summary        会话摘要
   * @param recent         最近对话
   * @param memory         长期记忆块
   * @param citationsBody  编号摘录
   * @return 切片结果
   */
  public SliceResult slice(String summary, String recent, String memory, String citationsBody) {
    int totalTokenBudget = resolveTotalTokenBudget();
    int summaryChars = charsForShare(totalTokenBudget, 0.15, 600);
    int recentChars = charsForShare(totalTokenBudget, 0.25, 2400);
    int memoryChars = charsForShare(totalTokenBudget, 0.15, 800);
    int citationChars = charsForShare(totalTokenBudget, 0.45, 4000);

    List<String> truncated = new ArrayList<>();
    String summaryOut = truncate(summary, summaryChars, "summary", truncated);
    String recentOut = truncate(recent, recentChars, "recent", truncated);
    String memoryOut = truncate(memory, memoryChars, "memory", truncated);
    String citationsOut = truncate(citationsBody, citationChars, "citations", truncated);
    return new SliceResult(summaryOut, recentOut, memoryOut, citationsOut, truncated);
  }

  /**
   * 是否发生截断。
   *
   * @param result 切片结果
   * @return 截断段名列表
   */
  public static Map<String, Object> truncationMeta(SliceResult result) {
    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("budgetTruncated", result.truncated());
    return meta;
  }

  /**
   * 总 token 预算。
   *
   * @return token
   */
  private int resolveTotalTokenBudget() {
    if (properties.getKnowledge() != null
        && properties.getKnowledge().getContext() != null
        && properties.getKnowledge().getContext().getTokenBudget() > 0) {
      return properties.getKnowledge().getContext().getTokenBudget();
    }
    // 兼容旧配置：未单独配 context 时回退 agent.token-budget
    if (properties.getKnowledge() != null
        && properties.getKnowledge().getAgent() != null
        && properties.getKnowledge().getAgent().getTokenBudget() > 0) {
      return properties.getKnowledge().getAgent().getTokenBudget();
    }
    return 8000;
  }

  /**
   * 按份额换算字符上限。
   *
   * @param totalTokens 总 token
   * @param share       份额
   * @param softMax     软上限
   * @return 字符数
   */
  private static int charsForShare(int totalTokens, double share, int softMax) {
    int chars = (int) Math.round(totalTokens * share * 4.0);
    return Math.min(Math.max(chars, 200), softMax);
  }

  /**
   * 截断并记录。
   *
   * @param raw       原文
   * @param maxChars  上限
   * @param segment   段名
   * @param truncated 截断列表
   * @return 结果
   */
  private static String truncate(
      String raw, int maxChars, String segment, List<String> truncated) {
    String text = StrUtil.nullToEmpty(raw);
    if (text.length() <= maxChars) {
      return text;
    }
    truncated.add(segment);
    return StrUtil.maxLength(text, maxChars);
  }
}
