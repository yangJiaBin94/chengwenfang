package com.workbench.notification.facade.dto.view;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 通知列表页视图。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationListView {
  /** 通知列表 */
  private List<NotificationView> items;
  /** 下一页游标 */
  private String nextCursor;
  /** 未读数量 */
  private Long unreadCount;
}
