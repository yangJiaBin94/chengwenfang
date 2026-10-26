package com.chengwenfang.quality.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 单条审核规则结果（对齐前端 AuditItemData 核心字段）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditItemView {
  /** 规则码。 */
  private String code;
  /** 标题。 */
  private String title;
  /** 详情。 */
  private String detail;
  /** 状态 pass / warn / fail。 */
  private String status;
  /** 是否可由文本 AI 修复。 */
  private Boolean fixableByTextAi;
  /** 是否阻断。 */
  private Boolean blocking;
  /** 位置。 */
  private String location;
  /** 建议。 */
  private String suggestion;
}