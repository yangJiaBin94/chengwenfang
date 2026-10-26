package com.workbench.quality.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审稿修复候选，对应 {@code repair_candidates}。
 */
@TableName("repair_candidates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotArticleRepairCandidateEntity {
  /** 主键。 */
  @TableId("id")
  private String id;
  /** 来源审核运行（原 review_run_id）。 */
  @TableField("audit_run_id")
  private String auditRunId;
  /** 产出 ID。 */
  @TableField("output_id")
  private String outputId;
  /** 异步任务 ID。 */
  @TableField("job_id")
  private String jobId;
  /** 状态：pending / accepted / rejected。 */
  @TableField("status")
  private String status;
  /** 是否全部修复（原 issue_ids 为空时）。 */
  @TableField("repair_all")
  private Boolean repairAll;
  /** 候选正文文件。 */
  @TableField("body_file_id")
  private String bodyFileId;
  /** 相对原文的差异摘要。 */
  @TableField("diff_summary")
  private String diffSummary;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
  /** 人工采纳/拒绝时间。 */
  @TableField("decided_at")
  private LocalDateTime decidedAt;
}
