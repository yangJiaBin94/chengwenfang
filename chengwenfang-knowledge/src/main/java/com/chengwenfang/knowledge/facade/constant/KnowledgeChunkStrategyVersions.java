package com.chengwenfang.knowledge.facade.constant;

/**
 * 切块策略版本：算法或契约变更时 bump，触发同正文文档重切。
 */
public final class KnowledgeChunkStrategyVersions {

  /** 当前策略版本（c2：统一 Chunker / 共享滑窗 / 表·幻灯契约加固）。 */
  public static final String CURRENT = "c2";

  private KnowledgeChunkStrategyVersions() {}
}
