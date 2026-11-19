package com.chengwenfang.knowledge.facade.dto.view;

import com.chengwenfang.knowledge.facade.enums.KnowledgeDocumentStatus;
import com.chengwenfang.knowledge.facade.enums.KnowledgeMediaKind;
import com.chengwenfang.knowledge.facade.enums.KnowledgeSourceKind;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 知识库文档视图。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocumentView {
  /** 文档 ID。 */
  private String id;
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
  /** 索引状态。 */
  private KnowledgeDocumentStatus status;
  /** 切块数量。 */
  private Integer chunkCount;
  /** 内容哈希。 */
  private String contentHash;
  /** 来源版本戳。 */
  private String sourceVersionStamp;
  /** 前端深链。 */
  private String deepLink;
  /** 原始附件文件 ID。 */
  private String originFileId;
  /** 归一化正文文件 ID（引用预览用）。 */
  private String textFileId;
  /** 失败原因。 */
  private String errorMessage;
  /** 创建时间。 */
  private LocalDateTime createdAt;
  /** 更新时间。 */
  private LocalDateTime updatedAt;
}
