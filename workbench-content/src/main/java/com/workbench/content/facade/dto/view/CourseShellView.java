package com.workbench.content.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 课程壳（原稿/公众号占位）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseShellView {
  /** 课程 ID。 */
  private String courseId;

  /** 标题。 */
  private String title;

  /** 原稿文章 ID（壳主键；与 documentId 同值）。 */
  private String draftArticleId;

  /** 公众号文章 ID（壳主键；与 documentId 同值）。 */
  private String wechatArticleId;

  /** 原稿统一文档 ID。 */
  private String draftDocumentId;

  /** 公众号统一文档 ID。 */
  private String wechatDocumentId;

  /** 状态。 */
  private String status;
}
