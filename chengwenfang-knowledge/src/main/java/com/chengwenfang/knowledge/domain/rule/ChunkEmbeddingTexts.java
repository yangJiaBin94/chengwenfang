package com.chengwenfang.knowledge.domain.rule;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.knowledge.facade.constant.KnowledgeChunkConstants;

/**
 * 切块 embedding 文本拼装与截断。
 */
public final class ChunkEmbeddingTexts {

  private ChunkEmbeddingTexts() {}

  /**
   * 拼接 title / headingPath / content，并截断至嵌入上限。
   *
   * @param title       标题
   * @param headingPath 标题路径
   * @param content     正文
   * @return embedding 文本
   */
  public static String build(String title, String headingPath, String content) {
    StringBuilder embedding = new StringBuilder();
    if (StrUtil.isNotBlank(title)) {
      embedding.append(StrUtil.trim(title));
    }
    if (StrUtil.isNotBlank(headingPath)) {
      if (!embedding.isEmpty()) {
        embedding.append('\n');
      }
      embedding.append(StrUtil.trim(headingPath));
    }
    if (StrUtil.isNotBlank(content)) {
      if (!embedding.isEmpty()) {
        embedding.append("\n\n");
      }
      embedding.append(content);
    }
    return truncate(embedding.toString());
  }

  /**
   * 截断过长 embedding（不改 content）。
   *
   * @param embedding 原文
   * @return 截断结果
   */
  public static String truncate(String embedding) {
    String text = StrUtil.nullToEmpty(embedding);
    int max = KnowledgeChunkConstants.MAX_EMBEDDING_CHARS;
    if (text.length() <= max) {
      return text;
    }
    return text.substring(0, max);
  }
}
