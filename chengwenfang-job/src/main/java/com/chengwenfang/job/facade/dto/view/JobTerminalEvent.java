package com.chengwenfang.job.facade.dto.view;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SSE/终态事件（*Event），非 View；按类命名词典冻结。
 * <p>任务进入 succeeded/failed 终态后发布的应用事件，供领域侧联动（如更新业务实体状态）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobTerminalEvent {
  /** 任务 ID。 */
  private String jobId;
  /** 任务类型。 */
  private String type;
  /** 业务实体类型。 */
  private String entityType;
  /** 业务实体 ID。 */
  private String entityId;
  /** 终态状态。 */
  private String status;
  /** 错误码（失败时）。 */
  private String errorCode;
  /** 错误信息（失败时）。 */
  private String errorMessage;
  /** 任务输出快照。 */
  private Map<String, Object> output;
}
