package com.workbench.content.facade.dto.request;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 热点主题搜索词 AI 建议请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsSearchQuerySuggestRequest {
  /** 主题名称。 */
  private String name;
  /** 主题说明。 */
  private String description;
  /** 领域标签文案（如科技产品 / 自定义领域名）。 */
  private String domainLabel;
  /** 关注实体 / 关键词簇。 */
  @Builder.Default
  private List<String> mustWatch = new ArrayList<>();
}
