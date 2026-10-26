package com.chengwenfang.notification.facade.constant;

/**
 * 站内通知 type 常量（开集；与 {@code notifications.notification_type} 列对齐）。
 */
public final class NotificationTypes {
  /** 课程全文生成成功。 */
  public static final String COURSE_GENERATION_SUCCEEDED = "course_generation_succeeded";
  /** 课程全文生成失败。 */
  public static final String COURSE_GENERATION_FAILED = "course_generation_failed";
  /** 热点文章生成成功。 */
  public static final String HOT_NEWS_GENERATION_SUCCEEDED = "hot_news_generation_succeeded";
  /** 热点文章生成失败。 */
  public static final String HOT_NEWS_GENERATION_FAILED = "hot_news_generation_failed";
  /** 其它 Job 终态成功（具体业务看 metadata.jobType）。 */
  public static final String JOB_TERMINAL_SUCCEEDED = "job_terminal_succeeded";
  /** 其它 Job 终态失败。 */
  public static final String JOB_TERMINAL_FAILED = "job_terminal_failed";

  private NotificationTypes() {}
}
