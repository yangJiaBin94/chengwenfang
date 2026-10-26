package com.workbench.mode.hotnews.facade.constant;

/**
 * 热点文章状态词（开集，与 content_documents.status / 历史壳 status 对齐）。
 */
public final class HotNewsArticleStatuses {
  /** 草稿占位（建壳未生成）。 */
  public static final String DRAFT = "draft";
  /** 成文任务进行中。 */
  public static final String GENERATING = "generating";
  /** 生成完成可编辑。 */
  public static final String READY = "ready";
  /** 已产出改写候选。 */
  public static final String REWRITTEN = "rewritten";

  private HotNewsArticleStatuses() {}
}
