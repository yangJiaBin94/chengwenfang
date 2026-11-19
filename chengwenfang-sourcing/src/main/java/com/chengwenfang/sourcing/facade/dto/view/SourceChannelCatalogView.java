package com.chengwenfang.sourcing.facade.dto.view;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 信源沉淀池条目视图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceChannelCatalogView {
  /** 主键。 */
  private String id;
  /** 业务键。 */
  private String code;
  /** 展示名。 */
  private String name;
  /** 抓取类型。 */
  private String channelType;
  /** 抓取入口 URL。 */
  private String baseUrl;
  /** 可选配置 JSON 原文。 */
  private String configJson;
  /** 场景标签。 */
  @Builder.Default
  private List<String> tags = new ArrayList<>();
  /** 是否启用。 */
  private Boolean enabled;
  /** 排序。 */
  private Integer sortOrder;
}
