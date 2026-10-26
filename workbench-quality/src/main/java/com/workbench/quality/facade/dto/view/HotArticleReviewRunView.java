package com.workbench.quality.facade.dto.view;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 热点审稿运行视图。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotArticleReviewRunView {
  /** 运行 ID */
  private String id;
  /** 文章 ID */
  private String articleId;
  /** 任务 ID */
  private String jobId;
  /** 状态 */
  private String status;
  /** 模式 */
  private String mode;
  /** 内容哈希 */
  private String contentHash;
  /** 标准版本 */
  private String standardVersion;
  /** 是否有阻断失败 */
  private Boolean blockingFailed;
  /** 创建时间 */
  private LocalDateTime createdAt;
  /** 结束时间 */
  private LocalDateTime finishedAt;
}
