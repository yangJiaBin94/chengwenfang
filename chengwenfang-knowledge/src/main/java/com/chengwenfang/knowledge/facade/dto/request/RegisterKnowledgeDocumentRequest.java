package com.chengwenfang.knowledge.facade.dto.request;

import com.chengwenfang.knowledge.facade.enums.KnowledgeMediaKind;
import com.chengwenfang.knowledge.facade.enums.KnowledgeSourceKind;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 登记或更新知识库文档索引来源。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterKnowledgeDocumentRequest {
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
  /** 来源版本戳。 */
  private String sourceVersionStamp;
  /** 前端深链。 */
  private String deepLink;
  /** 连接器已读出的原始正文（可选）。 */
  private String rawText;
  /** 原始附件文件 ID（上传场景）。 */
  private String originFileId;
  /** 归一化/预写正文文件 ID（登记时已落盘的文本）。 */
  private String textFileId;
}
