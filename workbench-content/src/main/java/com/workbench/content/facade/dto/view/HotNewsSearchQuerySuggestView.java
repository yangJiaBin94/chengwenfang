package com.workbench.content.facade.dto.view;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 热点主题搜索词 AI 建议结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsSearchQuerySuggestView {
  /** 规范化后的短搜索词。 */
  @Builder.Default
  private List<String> queries = new ArrayList<>();
  /** 是否来自 AI（false 表示规则回退）。 */
  private boolean fromAi;
}
