package com.chengwenfang.knowledge.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 领域侧证据摘录（忠实度规则使用，不依赖 Facade View）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceSnippet {
  /** 标题。 */
  private String title;
  /** 正文摘录。 */
  private String snippet;
}
