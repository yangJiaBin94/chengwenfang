package com.chengwenfang.content.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 人工审核视图（对齐前端 HumanReviewData）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HumanReviewView {
  /** 审核 ID。 */
  private String id;

  /** 文章 ID。 */
  private String articleId;

  /** 统一文档 ID。 */
  private String documentId;

  /** 文档版本 ID（content_document_versions.id）。 */
  private String documentVersionId;

  /** 版本号。 */
  private Integer versionNo;

  /** 状态 approved / rejected。 */
  private String status;

  /** 评论。 */
  private String comment;

  /** 审核时间。 */
  private String reviewedAt;

  /**
   * 兼容旧前端字段：等同 {@link #documentVersionId}。
   *
   * @return 文档版本 ID
   * @deprecated 请改用 {@link #getDocumentVersionId()}
   */
  @Deprecated
  public String getArticleVersionId() {
    return documentVersionId;
  }
}
