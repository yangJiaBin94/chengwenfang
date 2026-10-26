package com.chengwenfang.mode.hotnews.facade.dto.view;

import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 选题资料池分页结果（含主题级文章/视频统计）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsResearchItemPageView {
  /** 当前页资料。 */
  @Builder.Default
  private List<HotNewsResearchItemView> items = Collections.emptyList();
  /** 当前筛选条件下的总条数。 */
  private long total;
  /** 当前页码（从 1 开始）。 */
  private int page;
  /** 每页条数。 */
  private int pageSize;
  /** 选题下资料总数（不受列表筛选影响）。 */
  private long itemCount;
  /** 选题下文章资料数。 */
  private long articleCount;
  /** 选题下视频资料数。 */
  private long videoCount;
  /** 选题下尚未解析成功的视频数。 */
  private long pendingVideoCount;
}
