package com.chengwenfang.knowledge.facade.constant;

/**
 * 知识库切块尺寸与护栏常量（按字符计，非 tokenizer）。
 */
public final class KnowledgeChunkConstants {

  /** 目标块长（字符）。 */
  public static final int TARGET_CHARS = 600;

  /** 超过此长度才滑窗（字符）。 */
  public static final int MAX_CHARS = 800;

  /** 默认相邻块重叠（字符）。 */
  public static final int OVERLAP_CHARS = 80;

  /** 视频转写相邻块重叠（字符）。 */
  public static final int VIDEO_OVERLAP_CHARS = 120;

  /** 过短块合并阈值（字符）。 */
  public static final int MIN_MERGE_CHARS = 120;

  /** 软断句回扫窗口（字符）。 */
  public static final int SOFT_BREAK_LOOKBACK = 120;

  /** 单文件表格最大数据行数；超限索引失败。 */
  public static final int MAX_TABLE_ROWS = 20_000;

  /** 送入嵌入模型的文本上限（字符）；超出只截断 embeddingText。 */
  public static final int MAX_EMBEDDING_CHARS = 8_000;

  /** CSV 归一化时的约定 sheet 名（写入 headingPath）。 */
  public static final String CSV_SHEET_NAME = "csv";

  private KnowledgeChunkConstants() {}
}
