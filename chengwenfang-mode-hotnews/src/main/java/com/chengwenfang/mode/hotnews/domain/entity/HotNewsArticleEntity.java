package com.chengwenfang.mode.hotnews.domain.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 热点文章内存投影（原稿/公众号双轨）。
 * <p>权威在 works / hotnews_works / work_outputs，本类不直接落库。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsArticleEntity {
  /** 主键 ID（= works.id = hotnews_works.work_id）。 */
  private String id;
  /** 选题/话题 ID。 */
  private String topicId;
  /** 选题 Brief ID。 */
  private String briefId;
  /** 文章标题。 */
  private String title;
  /** 内部原稿 Markdown。 */
  private String draftContent;
  /** 公众号正文 Markdown。 */
  private String content;
  /** 正文内容哈希。 */
  private String contentHash;
  /** 草稿内容哈希。 */
  private String draftContentHash;
  /** 正文字数。 */
  private Integer wordCount;
  /** 当前版本号。 */
  private Integer currentVersionNo;
  /** 热点文章状态。 */
  private String status;
  /** 工作流运行 ID。 */
  private String workflowRunId;
  /** 生成参数 JSON。 */
  private String generationSettingsJson;
  /** 生成时锁定的来源快照 JSON。 */
  private String sourceSnapshotJson;
  /** 创建时间。 */
  private LocalDateTime createdAt;
  /** 更新时间。 */
  private LocalDateTime updatedAt;
}
