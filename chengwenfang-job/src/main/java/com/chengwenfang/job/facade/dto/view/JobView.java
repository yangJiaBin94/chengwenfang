package com.chengwenfang.job.facade.dto.view;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 对外任务视图。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobView {
  /** 任务 ID。 */
  private String id;
  /** 任务类型。 */
  private String type;
  /** 状态。 */
  private String status;
  /** 业务实体类型。 */
  private String entityType;
  /** 业务实体 ID。 */
  private String entityId;
  /**
   * 统一文档 ID（从 input_json.documentId 投影；热点公众号轨 / 课程文章）。
   * 供前端深链走 /api/documents，可空。
   */
  private String documentId;
  /** 热点草稿轨文档 ID（从 input_json.draftDocumentId 投影；可空）。 */
  private String draftDocumentId;
  /** 进度 0–100。 */
  private int progress;
  /** 当前步骤。 */
  private String currentStep;
  /** 最近一次进度说明（来自 progress 事件，可空）。 */
  private String progressMessage;
  /** 最近一次进度附加信息（如 total/current/title，可空）。 */
  private Map<String, Object> progressMeta;
  /** 关联工作流运行 ID（可空）。 */
  private String workflowRunId;
  /** 输出载荷。 */
  private Map<String, Object> output;
  /** 错误码。 */
  private String errorCode;
  /** 错误信息。 */
  private String errorMessage;
  /** 重试次数。 */
  private int retryCount;
  /** 创建时间。 */
  private LocalDateTime createdAt;
  /** 开始时间。 */
  private LocalDateTime startedAt;
  /** 结束时间。 */
  private LocalDateTime finishedAt;
}
