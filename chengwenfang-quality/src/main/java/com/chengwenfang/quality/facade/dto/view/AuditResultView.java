package com.chengwenfang.quality.facade.dto.view;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 正文审核结果汇总。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditResultView {
  /** 总分。 */
  private int score;
  /** 是否存在阻断问题。 */
  private boolean blocking;
  /** 通过条数。 */
  private int passed;
  /** 警告条数。 */
  private int warnings;
  /** 失败条数。 */
  private int failed;
  /** 问题列表（非 pass）。 */
  private List<AuditIssueView> issues;
  /** 全部规则项。 */
  private List<AuditItemView> items;
}