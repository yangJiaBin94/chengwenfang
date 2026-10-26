package com.chengwenfang.sourcing.facade.dto.view;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 热点抓取运行视图（对齐前端 HotNewsRun）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsFetchRunView {
  /** 运行 ID */
  private String id;
  /** 主题 ID */
  private String themeId;
  /** 状态 */
  private String status;
  /** 触发方式 */
  private String trigger;
  /** 请求的频道 ID 列表 */
  private List<String> requestedChannels;
  /** 开始时间 */
  private LocalDateTime startedAt;
  /** 结束时间 */
  private LocalDateTime finishedAt;
  /** 总条目数 */
  private Integer totalItems;
  /** 新增条目数 */
  private Integer newItems;
  /** 去重条目数 */
  private Integer dedupedItems;
  /** 错误摘要 */
  private String errorSummary;
  /** 频道子运行 */
  private List<HotNewsChannelRunView> channelRuns;
  /** 关联任务 ID */
  private String jobId;
  /** 创建时间 */
  private LocalDateTime createdAt;
}
