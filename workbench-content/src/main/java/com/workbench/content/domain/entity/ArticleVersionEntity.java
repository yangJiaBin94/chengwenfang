package com.workbench.content.domain.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文章版本快照投影（非表实体；权威在 content_document_versions）。
 * <p>供审核绑定等仍依赖版本 id/正文形状的调用方使用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleVersionEntity {
  /** 版本 id（content_document_versions.id）。 */
  private String id;
  /** 文章 ID。 */
  private String articleId;
  /** 版本号（同一文章内递增）。 */
  private Integer versionNo;
  /** 正文内容。 */
  private String content;
  /** 内容哈希。 */
  private String contentHash;
  /** 版本来源：generation / manual_edit / restore / repair_* 等。 */
  private String source;
  /** 版本产生原因说明。 */
  private String reason;
  /** 关联审核运行 ID。 */
  private String auditRunId;
  /** 关联工作流运行 ID。 */
  private String workflowRunId;
  /** 创建时间。 */
  private LocalDateTime createdAt;
}
