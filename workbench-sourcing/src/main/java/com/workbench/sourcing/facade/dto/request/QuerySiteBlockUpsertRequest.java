package com.workbench.sourcing.facade.dto.request;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 搜索词 × 网站拉黑 upsert 请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuerySiteBlockUpsertRequest {
  /** 主题 ID。 */
  private String themeId;
  /** 渠道类型。 */
  private String channelType;
  /** 搜索词。 */
  private String searchQuery;
  /** 站点键。 */
  private String siteKey;
  /** 本轮偏题命中增量。 */
  private Integer hitDelta;
  /** 样例标题（最多 5 条，合并去重）。 */
  @Builder.Default
  private List<String> sampleTitles = new ArrayList<>();
  /** 原因摘要。 */
  private String reason;
}
