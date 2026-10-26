package com.chengwenfang.mode.hotnews.facade.dto.view;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 选题证据视图（对齐前端 HotNewsEvidence）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsEvidenceView {
  /** 证据 ID */
  private String id;
  /** 选题 ID */
  private String topicId;
  /** 陈述 */
  private String statement;
  /** 证据类型 */
  private String evidenceType;
  /** 信源条目 ID */
  private String sourceItemId;
  /** 来源标题 */
  private String sourceTitle;
  /** 来源链接 */
  private String sourceUrl;
  /** 来源名 */
  private String sourceName;
  /** 站点来源展示名 */
  private String siteLabel;
  /** 媒体类型 article/video */
  private String mediaType;
  /** 关联信源发布时间；未知则为 null */
  private LocalDateTime publishedAt;
  /** 状态 */
  private String status;
  /** 审核人 */
  private String reviewer;
  /** 备注 */
  private String note;
  /** 标题（兼容） */
  private String title;
  /** 链接（兼容） */
  private String url;
  /** 摘录（兼容） */
  private String snippet;
  /** 角色（兼容） */
  private String role;
  /** 创建时间 */
  private LocalDateTime createdAt;
}
