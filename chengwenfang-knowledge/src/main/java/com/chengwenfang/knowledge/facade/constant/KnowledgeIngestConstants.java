package com.chengwenfang.knowledge.facade.constant;

/**
 * 知识库手动摄入与抽取门槛常量。
 */
public final class KnowledgeIngestConstants {

  /** 归一化正文最短字符数；不足则标记索引失败。 */
  public static final int MIN_EXTRACTED_CHARS = 50;

  /** 手动上传单文件默认上限（字节）。 */
  public static final long MAX_UPLOAD_BYTES = 20L * 1024 * 1024;

  /** 允许的上传扩展名（小写，无点）；图片需带标注走 register，不经裸上传。 */
  public static final String[] ALLOWED_UPLOAD_EXTENSIONS = {
      "txt", "md", "markdown", "docx", "pdf", "pptx", "xlsx", "csv"
  };

  private KnowledgeIngestConstants() {}
}
