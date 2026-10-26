package com.chengwenfang.content.domain.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 课程文章内存投影（原稿 course_draft / 公众号 wechat_article）。
 * <p>权威在 work_outputs，本类不再落库。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleEntity {
  /** 主键 ID（= work_outputs.id）。 */
  private String id;
  /** 课程 ID。 */
  private String courseId;
  /** 文章类型：course_draft / wechat_article。 */
  private String kind;
  /** 文章标题。 */
  private String title;
  /** 正文 Markdown。 */
  private String content;
  /** 学习同步相对路径。 */
  private String filePath;
  /** 源文件 stored_files id。 */
  private String sourceFileId;
  /** 内容哈希。 */
  private String contentHash;
  /** 字数。 */
  private Integer wordCount;
  /** 文章状态。 */
  private String status;
  /** 乐观锁令牌。 */
  private Long versionToken;
  /** 当前内容版本号。 */
  private Integer currentVersionNo;
  /** 最近更新时间。 */
  private LocalDateTime updatedAt;
}
