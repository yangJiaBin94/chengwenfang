package com.workbench.quality.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审核问题的定位或证据，对应 {@code audit_issue_refs}。
 */
@TableName("audit_issue_refs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditIssueRefEntity {
  /** 主键。 */
  @TableId("id")
  private String id;
  /** 问题 ID。 */
  @TableField("issue_id")
  private String issueId;
  /** 类型：location / evidence。 */
  @TableField("ref_type")
  private String refType;
  /** 顺序。 */
  @TableField("seq")
  private Integer seq;
  /** 内容（locations / evidence 元素的 JSON 文本）。 */
  @TableField("content")
  private String content;
}
