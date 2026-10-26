package com.chengwenfang.content.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 文章对外视图（对齐前端 ArticleData）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleView {
  /** 文章 ID。 */
  private String id;

  /** 统一文档 ID（课程侧与 articles.id 相同）。 */
  private String documentId;

  /** 所属课程 ID。 */
  private String courseId;

  /** 文章类型 course_draft / wechat_article。 */
  private String kind;

  /** 标题。 */
  private String title;

  /** 正文 Markdown。 */
  private String content;

  /** 内容哈希。 */
  private String contentHash;

  /** 字数。 */
  private Integer wordCount;

  /** 状态。 */
  private String status;

  /** 乐观锁版本令牌。 */
  private Long versionToken;

  /** 当前版本号。 */
  private Integer currentVersionNo;

  /** 更新时间（ISO-8601）。 */
  private String updatedAt;
}
