package com.workbench.notification.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 通知汇总视图（对齐前端 NotificationSummary）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSummaryView {
  /** 未读数量 */
  private Long unreadCount;
  /** 最新创建时间 */
  private String latestCreatedAt;
}
