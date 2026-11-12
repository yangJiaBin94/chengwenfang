package com.chengwenfang.knowledge.domain.model;

import com.chengwenfang.knowledge.facade.enums.KnowledgeMediaKind;
import com.chengwenfang.knowledge.facade.enums.KnowledgeSourceKind;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 知识库来源侧待入库草稿（尚未持久化为 knowledge_documents）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeIngestDraft {
  /** 来源种类。 */
  private KnowledgeSourceKind sourceKind;
  /** 来源侧主键。 */
  private String sourceRefId;
  /** 介质种类。 */
  private KnowledgeMediaKind mediaKind;
  /** 标题。 */
  private String title;
  /** 主题 ID。 */
  private String themeId;
  /** 可检索文本哈希。 */
  private String contentHash;
  /** 来源版本戳（如产出版本号）。 */
  private String sourceVersionStamp;
  /** 前端深链。 */
  private String deepLink;
  /** 原始正文字符串（连接器已读出时携带）。 */
  private String rawText;
  /** 原始附件文件 ID（上传类来源）。 */
  private String originFileId;
}
