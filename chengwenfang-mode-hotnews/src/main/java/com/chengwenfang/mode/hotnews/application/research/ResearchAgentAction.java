package com.chengwenfang.mode.hotnews.application.research;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 资料补充 Agent 单条动作。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResearchAgentAction {
  /** 动作类型：search / fetch / finish。 */
  private String type;
  /** search：检索词列表。 */
  @Builder.Default
  private List<String> queries = new ArrayList<>();
  /** fetch：待抓取 URL（须已在命中池）。 */
  @Builder.Default
  private List<String> urls = new ArrayList<>();
  /** finish：选用条目。 */
  @Builder.Default
  private List<ResearchAgentSelectedItem> items = new ArrayList<>();
}
