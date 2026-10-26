package com.workbench.quality.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 审核问题项（非 pass 汇总）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditIssueView {
  /** 规则码。 */
  private String code;
  /** 标题。 */
  private String title;
  /** 严重级别。 */
  private String severity;
  /** 消息。 */
  private String message;
  /** 是否阻断。 */
  private Boolean blocking;
  /** 是否可由文本 AI 修复。 */
  private Boolean fixableByTextAi;
  /** 位置。 */
  private String location;
  /** 建议。 */
  private String suggestion;
}