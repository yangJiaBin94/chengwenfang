package com.chengwenfang.notification.facade.dto.view;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 站内通知视图（对齐前端 NotificationData）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationView {
  /** 通知 ID */
  private String id;
  /** 通知类型 */
  private String type;
  /** 级别 */
  private String level;
  /** 标题 */
  private String title;
  /** 正文 */
  private String message;
  /** 关联课程/实体 ID */
  private String courseId;
  /** 关联任务 ID */
  private String jobId;
  /** 关联工作流运行 ID */
  private String workflowRunId;
  /** 系列名 */
  private String seriesName;
  /** 课程/实体标题 */
  private String courseTitle;
  /** 课序号 */
  private Integer lessonNo;
  /** 状态 unread / read */
  private String status;
  /** 扩展元数据 */
  private Map<String, Object> metadata;
  /** 创建时间 */
  private String createdAt;
  /** 已读时间 */
  private String readAt;
}
