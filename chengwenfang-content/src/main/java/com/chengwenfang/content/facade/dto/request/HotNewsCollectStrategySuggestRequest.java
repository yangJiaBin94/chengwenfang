package com.chengwenfang.content.facade.dto.request;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 热点主题采集策略 AI 建议请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsCollectStrategySuggestRequest {
  /** 主题名称。 */
  private String name;
  /** 主题说明。 */
  private String description;
  /** 领域标签。 */
  private String domainLabel;
  /** 意图摘要。 */
  private String intentSummary;
  /** 要的素材。 */
  @Builder.Default
  private List<String> materialWants = new ArrayList<>();
  /** 不要的素材。 */
  @Builder.Default
  private List<String> materialAvoids = new ArrayList<>();
}
