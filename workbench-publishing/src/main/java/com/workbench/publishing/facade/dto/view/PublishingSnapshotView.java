package com.workbench.publishing.facade.dto.view;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 发布快照视图（对齐前端 PublishingSnapshotData）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishingSnapshotView {
  /** 快照 ID。 */
  private String id;
  /** 文章 ID。 */
  private String articleId;
  /** 文章版本号。 */
  private Integer articleVersionNo;
  /** 主题版本 ID。 */
  private String themeVersionId;
  /** HTML 内容。 */
  private String html;
  /** HTML 哈希。 */
  private String htmlHash;
  /** Markdown 哈希。 */
  private String markdownHash;
  /** 图片数量。 */
  private int imageCount;
  /** 诊断信息。 */
  private List<Map<String, Object>> diagnostics;
  /** 创建时间。 */
  private String createdAt;
}