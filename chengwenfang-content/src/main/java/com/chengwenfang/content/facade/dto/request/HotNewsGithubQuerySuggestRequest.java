package com.chengwenfang.content.facade.dto.request;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 热点主题 GitHub 查询串 AI 建议请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsGithubQuerySuggestRequest {
  /** 主题名称。 */
  private String name;
  /** 主题说明。 */
  private String description;
  /** 领域标签文案。 */
  private String domainLabel;
  /** 已确认的元搜索短词（可选，辅助生成）。 */
  @Builder.Default
  private List<String> searchQueries = new ArrayList<>();
}
