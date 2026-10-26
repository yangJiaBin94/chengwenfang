package com.chengwenfang.content.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 文章版本视图（对齐前端 ArticleVersionData）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleVersionView {
  /** 版本 ID。 */
  private String id;

  /** 文章 ID。 */
  private String articleId;

  /** 版本号。 */
  private Integer versionNo;

  /** 正文。 */
  private String content;

  /** 内容哈希。 */
  private String contentHash;

  /** 来源。 */
  private String source;

  /** 原因说明。 */
  private String reason;

  /** 工作流运行 ID。 */
  private String workflowRunId;

  /** 审核运行 ID。 */
  private String auditRunId;

  /** 审核摘要。 */
  private ArticleVersionAuditView audit;

  /** 创建时间（ISO-8601）。 */
  private String createdAt;
}
