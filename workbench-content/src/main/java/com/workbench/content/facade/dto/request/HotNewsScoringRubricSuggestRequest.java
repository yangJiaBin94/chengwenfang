package com.workbench.content.facade.dto.request;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 热点主题评分量表 AI 生成请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsScoringRubricSuggestRequest {
  /** 主题名称。 */
  private String name;
  /** 主题说明。 */
  private String description;
  /** 领域标签文案。 */
  private String domainLabel;
  /** 搜索关键词。 */
  @Builder.Default
  private List<String> searchQueries = new ArrayList<>();
  /** 值得写标准（可选）。 */
  @Builder.Default
  private List<String> worthWriting = new ArrayList<>();
  /** 主要读者（可选）。 */
  @Builder.Default
  private List<String> readers = new ArrayList<>();
  /** 明确不做（可选）。 */
  private String outOfScope;
  /** 内容姿态（可选）。 */
  private String contentStance;
}
