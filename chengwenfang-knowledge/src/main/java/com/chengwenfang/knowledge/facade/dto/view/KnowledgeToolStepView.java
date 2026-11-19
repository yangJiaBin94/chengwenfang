package com.chengwenfang.knowledge.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单次工具调用信封（写入检索轨迹）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeToolStepView {
  /** 工具名（闭集见 KnowledgeToolNames）。 */
  private String name;
  /** 参数摘要。 */
  private String args;
  /** 结果摘要。 */
  private String resultSummary;
  /** 耗时毫秒。 */
  private Long latencyMs;
  /** 是否成功。 */
  private Boolean ok;
}
