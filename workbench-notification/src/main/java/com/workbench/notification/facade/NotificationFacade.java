package com.workbench.notification.facade;


import com.workbench.notification.facade.dto.view.NotificationListView;
import com.workbench.notification.facade.dto.view.NotificationSummaryView;
import com.workbench.notification.facade.dto.view.NotificationView;
import java.util.List;
import java.util.Map;

/** 站内通知门面：查询、已读与任务终态通知创建。 */
public interface NotificationFacade {
  /** 列出未读通知。 */
  List<NotificationView> listUnread();

  /** 按状态分页/限量列出通知。 */
  NotificationListView list(String status, Integer limit);

  /** 通知汇总（未读数等）。 */
  NotificationSummaryView summary();

  /** 将指定通知标为已读。 */
  NotificationView markRead(String id);

  /** 设置通知已读/未读。 */
  NotificationView markRead(String id, boolean read);

  /** 全部标为已读。 */
  NotificationSummaryView readAll();

  /** 任务终态时创建去重通知。 */
  NotificationView createForTerminalJob(
      String jobType,
      String jobId,
      String entityId,
      String status,
      String title,
      String message,
      Map<String, Object> metadata);
}
