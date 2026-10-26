package com.chengwenfang.quality.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 修复候选针对的问题，对应 {@code repair_candidate_issues}。
 */
@TableName("repair_candidate_issues")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepairCandidateIssueEntity {
  /** 主键。 */
  @TableId("id")
  private String id;
  /** 候选 ID。 */
  @TableField("candidate_id")
  private String candidateId;
  /** 顺序。 */
  @TableField("seq")
  private Integer seq;
  /** 问题 ID（原 issueIds 元素）。 */
  @TableField("issue_ref")
  private String issueRef;
}
