package com.chengwenfang.knowledge.domain.rule;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.knowledge.domain.entity.KnowledgeDocumentEntity;
import com.chengwenfang.knowledge.facade.constant.KnowledgeChunkStrategyVersions;
import com.chengwenfang.knowledge.facade.enums.KnowledgeDocumentStatus;

/**
 * 索引幂等跳过策略：正文 hash、切块策略版本、文档声明块数与落库块数一致才跳过。
 */
public final class KnowledgeIndexSkipPolicy {

  private KnowledgeIndexSkipPolicy() {}

  /**
   * 是否可跳过索引（仅看文档行字段；落库块数请用三参数重载）。
   *
   * @param document    文档行
   * @param contentHash 归一化正文哈希
   * @return 跳过时 true
   */
  public static boolean shouldSkip(KnowledgeDocumentEntity document, String contentHash) {
    return shouldSkip(document, contentHash, ObjectUtil.defaultIfNull(
        document == null ? null : document.getChunkCount(), 0));
  }

  /**
   * 是否可跳过索引。
   *
   * @param document             文档行
   * @param contentHash          归一化正文哈希
   * @param persistedChunkCount  当前索引版本下切块表实际行数
   * @return 跳过时 true
   */
  public static boolean shouldSkip(
      KnowledgeDocumentEntity document, String contentHash, int persistedChunkCount) {
    if (document == null) {
      return false;
    }
    if (!KnowledgeDocumentStatus.READY.matches(document.getStatus())) {
      return false;
    }
    int chunkCount = ObjectUtil.defaultIfNull(document.getChunkCount(), 0);
    if (chunkCount <= 0 || persistedChunkCount != chunkCount) {
      return false;
    }
    if (!StrUtil.equals(contentHash, document.getContentHash())) {
      return false;
    }
    return StrUtil.equals(
        KnowledgeChunkStrategyVersions.CURRENT, document.getChunkStrategyVersion());
  }
}
