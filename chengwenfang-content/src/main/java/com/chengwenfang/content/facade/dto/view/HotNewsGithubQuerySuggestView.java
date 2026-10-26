package com.chengwenfang.content.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 热点主题 GitHub 查询串 AI 建议结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsGithubQuerySuggestView {
  /** GitHub Search 查询串（如 {@code AI OR LLM OR interview}）。 */
  private String query;
  /** 是否来自 AI（false 表示未产出，勿当生成成功）。 */
  private boolean fromAi;
}
