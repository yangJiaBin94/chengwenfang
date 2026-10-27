package com.chengwenfang.content.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 主题列表附加指标（由模式层 {@code ThemeListMetricsContributor} 贡献）。
 * <p>content 核心不感知热点表结构；无贡献者时计数视为 0。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThemeListMetrics {

  /** 来源条目数（如 source_items）。 */
  private long itemCount;

  /** 候选选题数（如 status=candidate 的热点选题）。 */
  private long candidateCount;
}
