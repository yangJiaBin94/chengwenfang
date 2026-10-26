package com.workbench.sourcing.facade.dto.view;

import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 热点信源频道分页结果（含主题级启用/停用统计，供来源管理页展示）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsChannelPageView {
  /** 当前页频道列表。 */
  @Builder.Default
  private List<HotNewsChannelView> items = Collections.emptyList();
  /** 当前筛选条件下的总条数。 */
  private long total;
  /** 当前页码（从 1 开始）。 */
  private int page;
  /** 每页条数。 */
  private int pageSize;
  /** 主题下已启用频道数（不受列表筛选影响）。 */
  private long enabledCount;
  /** 主题下已停用频道数（不受列表筛选影响）。 */
  private long disabledCount;
}
