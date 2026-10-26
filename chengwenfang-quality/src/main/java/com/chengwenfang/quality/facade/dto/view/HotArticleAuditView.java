package com.chengwenfang.quality.facade.dto.view;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 热点文章审稿汇总视图（对齐前端 HotNewsAudit）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotArticleAuditView {
  /** 审稿运行 ID */
  private String id;
  /** 文章 ID */
  private String articleId;
  /** 内容哈希 */
  private String contentHash;
  /** 任务 ID */
  private String jobId;
  /** 状态 */
  private String status;
  /** 规则引擎状态 */
  private String ruleStatus;
  /** AI 引擎状态 */
  private String aiStatus;
  /** 通过数 */
  private Integer passedCount;
  /** 告警数 */
  private Integer warningCount;
  /** 失败数 */
  private Integer failedCount;
  /** 阻断数 */
  private Integer blockingCount;
  /** 可修复数 */
  private Integer repairableCount;
  /** 标准版本 */
  private String standardVersion;
  /** 通过数（兼容） */
  private Integer passed;
  /** 告警数（兼容） */
  private Integer warnings;
  /** 失败数（兼容） */
  private Integer failed;
  /** 是否有阻断失败 */
  private Boolean blockingFailed;
  /** 摘要 */
  private String summary;
  /** 创建时间 */
  private LocalDateTime createdAt;
  /** 裁决状态 */
  private String decisionStatus;
  /** 裁决备注 */
  private String decisionComment;
  /** 裁决时间 */
  private String decidedAt;
  /** 审稿条目 */
  private List<HotArticleReviewItemView> items;
  /** 结果列表（兼容） */
  private List<HotArticleReviewItemView> results;
}
