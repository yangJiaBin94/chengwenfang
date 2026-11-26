package com.chengwenfang.knowledge.facade.constant;

/**
 * 知识库向量点 payload / 元数据键名（与 Qdrant 过滤字段一致）。
 */
public final class KnowledgeVectorPayloadKeys {

  /** 正文 payload 键（LangChain4j {@code payloadTextKey}）。 */
  public static final String CONTENT = "content";
  /** 文档 ID。 */
  public static final String DOCUMENT_ID = "documentId";
  /** 所属用户 ID。 */
  public static final String OWNER_USER_ID = "ownerUserId";
  /** 来源类型。 */
  public static final String SOURCE_KIND = "sourceKind";
  /** 来源业务 ID。 */
  public static final String SOURCE_REF_ID = "sourceRefId";
  /** 媒体类型。 */
  public static final String MEDIA_KIND = "mediaKind";
  /** 标题。 */
  public static final String TITLE = "title";
  /** 标题路径。 */
  public static final String HEADING_PATH = "headingPath";
  /** 块序号。 */
  public static final String CHUNK_INDEX = "chunkIndex";
  /** 内容哈希。 */
  public static final String CONTENT_HASH = "contentHash";
  /** 主题 ID。 */
  public static final String THEME_ID = "themeId";
  /** 深链接。 */
  public static final String DEEP_LINK = "deepLink";
  /** 索引版本（蓝绿读路径）。 */
  public static final String INDEX_VERSION = "indexVersion";
  /** 嵌入模型名（防混模检索）。 */
  public static final String EMBEDDING_MODEL = "embeddingModel";

  private KnowledgeVectorPayloadKeys() {}
}
