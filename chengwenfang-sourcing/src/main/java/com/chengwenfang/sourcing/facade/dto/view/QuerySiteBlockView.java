package com.chengwenfang.sourcing.facade.dto.view;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 搜索词 × 网站拉黑视图。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuerySiteBlockView {
  /** 主键。 */
  private String id;
  /** 主题 ID。 */
  private String themeId;
  /** 渠道类型。 */
  private String channelType;
  /** 搜索词。 */
  private String searchQuery;
  /** 站点键。 */
  private String siteKey;
  /** 状态。 */
  private String status;
  /** 累计命中偏题次数。 */
  private Integer hitCount;
  /** 样例标题。 */
  @Builder.Default
  private List<String> sampleTitles = new ArrayList<>();
  /** 原因摘要。 */
  private String reason;
  /** 创建时间。 */
  private LocalDateTime createdAt;
  /** 更新时间。 */
  private LocalDateTime updatedAt;
}
