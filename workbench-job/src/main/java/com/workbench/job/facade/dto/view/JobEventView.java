package com.workbench.job.facade.dto.view;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 单条任务事件视图。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobEventView {
  /** 事件自增 ID。 */
  private long id;
  /** 事件类型。 */
  private String eventType;
  /** 步骤名。 */
  private String step;
  /** 进度。 */
  private int progress;
  /** 消息。 */
  private String message;
  /** 扩展载荷。 */
  private Map<String, Object> payload;
  /** 创建时间。 */
  private LocalDateTime createdAt;
}
