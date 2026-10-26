package com.chengwenfang.notification.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 课程生成/任务终态等站内通知实体，对应 notifications 表。 */
@TableName("notifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEntity {
  /** 主键 ID。 */
  @TableId("id")
  private String id;
  /** 通知类型（原 type）。 */
  @TableField("notification_type")
  private String notificationType;
  /** 级别：success / error。 */
  @TableField("level")
  private String level;
  /** 通知标题。 */
  @TableField("title")
  private String title;
  /** 通知正文。 */
  @TableField("message")
  private String message;
  /** 阅读状态：unread / read。 */
  @TableField("status")
  private String status;
  /** 已读时间。 */
  @TableField("read_at")
  private LocalDateTime readAt;
  /** 去重键，避免同一终态重复推送。 */
  @TableField("dedupe_key")
  private String dedupeKey;
  /** 异步任务 ID。 */
  @TableField("job_id")
  private String jobId;
  /** 任务类型。 */
  @TableField("job_type")
  private String jobType;
  /** 工作流运行 ID。 */
  @TableField("workflow_run_id")
  private String workflowRunId;
  /** 模式编码。 */
  @TableField("mode_code")
  private String modeCode;
  /** 主题 ID。 */
  @TableField("theme_id")
  private String themeId;
  /** 主题编码快照。 */
  @TableField("theme_code")
  private String themeCode;
  /** 主题名称快照。 */
  @TableField("theme_name")
  private String themeName;
  /** 作品 ID（原 courseId / 热点 articleId）。 */
  @TableField("work_id")
  private String workId;
  /** 产出 ID（原 documentId）。 */
  @TableField("output_id")
  private String outputId;
  /** 原稿产出 ID（原 draftDocumentId）。 */
  @TableField("draft_output_id")
  private String draftOutputId;
  /** 热点选题 ID。 */
  @TableField("topic_id")
  private String topicId;
  /** 抓取运行 ID。 */
  @TableField("fetch_run_id")
  private String fetchRunId;
  /** 调研运行 ID。 */
  @TableField("research_run_id")
  private String researchRunId;
  /** 主体标题快照（原 courseTitle）。 */
  @TableField("subject_title")
  private String subjectTitle;
  /** 热点文章标题快照。 */
  @TableField("article_title")
  private String articleTitle;
  /** 系列名称。 */
  @TableField("series_name")
  private String seriesName;
  /** 课时序号。 */
  @TableField("lesson_no")
  private Integer lessonNo;
  /** 失败错误码。 */
  @TableField("error_code")
  private String errorCode;
  /** 失败原始信息。 */
  @TableField("raw_error_message")
  private String rawErrorMessage;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
}
