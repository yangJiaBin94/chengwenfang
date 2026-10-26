package com.chengwenfang.quality.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审核问题明细，对应 {@code audit_issues}。
 */
@TableName("audit_issues")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleAuditIssueEntity {
  /** 主键。 */
  @TableId("id")
  private String id;
  /** 审核运行 ID。 */
  @TableField("audit_run_id")
  private String auditRunId;
  /** 顺序。 */
  @TableField("seq")
  private Integer seq;
  /** 规则码。 */
  @TableField("code")
  private String code;
  /** 标题。 */
  @TableField("title")
  private String title;
  /** 状态：passed / warn / failed。 */
  @TableField("status")
  private String status;
  /** 是否阻塞。 */
  @TableField("blocking")
  private Boolean blocking;
  /** 详情。 */
  @TableField("detail")
  private String detail;
  /** 引擎：rule 等。 */
  @TableField("engine")
  private String engine;
  /** 分类。 */
  @TableField("category")
  private String category;
  /** 严重度。 */
  @TableField("severity")
  private String severity;
  /** 是否可修复。 */
  @TableField("repairable")
  private Boolean repairable;
  /** 是否需人工确认。 */
  @TableField("requires_human_confirmation")
  private Boolean requiresHumanConfirmation;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
}
