package com.workbench.content.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 生成配图视图。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedImageView {
  /** 配图 ID。 */
  private String id;

  /** 文章 ID。 */
  private String articleId;

  /** 类型。 */
  private String kind;

  /** 序号。 */
  private Integer sequenceNo;

  /** 提示词。 */
  private String prompt;

  /** 尺寸。 */
  private String size;

  /** 文件 ID。 */
  private String fileId;

  /** 公开 URL。 */
  private String publicUrl;

  /** 内容哈希。 */
  private String contentHash;

  /** 宽度。 */
  private Integer width;

  /** 高度。 */
  private Integer height;

  /** 任务 ID。 */
  private String jobId;

  /** 创建时间。 */
  private String createdAt;
}
