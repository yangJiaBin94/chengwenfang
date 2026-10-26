package com.workbench.content.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 人工审核记录，对应 {@code human_reviews}。
 */
@TableName("human_reviews")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HumanReviewEntity {
  /** 主键。 */
  @TableId("id")
  private String id;
  /** 产出 ID。 */
  @TableField("output_id")
  private String outputId;
  /** 被审版本（work_output_versions.id）。 */
  @TableField("output_version_id")
  private String outputVersionId;
  /** 审核状态：approved / rejected。 */
  @TableField("status")
  private String status;
  /** 审核意见。 */
  @TableField("comment")
  private String comment;
  /** 审核完成时间。 */
  @TableField("reviewed_at")
  private LocalDateTime reviewedAt;
  /** 记录创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
}
