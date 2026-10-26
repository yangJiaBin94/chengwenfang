package com.chengwenfang.mode.hotnews.facade.dto.view;

import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 热点雷达选题分页结果（含雷达池级候选/总数统计，供页头指标）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsTopicPageView {
  /** 当前页选题。 */
  @Builder.Default
  private List<HotNewsTopicView> items = Collections.emptyList();
  /** 当前筛选条件下的总条数。 */
  private long total;
  /** 当前页码（从 1 开始）。 */
  private int page;
  /** 每页条数。 */
  private int pageSize;
  /** 雷达池内候选选题数（不受列表筛选影响）。 */
  private long candidateCount;
  /** 雷达池选题总数（不受列表筛选影响）。 */
  private long topicCount;
}
