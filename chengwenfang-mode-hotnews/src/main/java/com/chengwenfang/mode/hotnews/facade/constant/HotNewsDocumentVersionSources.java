package com.chengwenfang.mode.hotnews.facade.constant;

/**
 * 热点文章写入 content_documents / 版本行的来源码（开集）。
 */
public final class HotNewsDocumentVersionSources {
  /** 新建文章空正文基线。 */
  public static final String BASELINE = "baseline";
  /** 人工编辑正文。 */
  public static final String MANUAL_EDIT = "manual_edit";
  /** AI 成文双轨提交。 */
  public static final String AI_GENERATION = "ai_generation";
  /** 改写候选草稿。 */
  public static final String REWRITE_CANDIDATE = "rewrite_candidate";

  private HotNewsDocumentVersionSources() {}
}
