package com.chengwenfang.mode.hotnews.facade.dto.view;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 选题调研运行视图。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsResearchRunView {
  /** 运行 ID */
  private String id;
  /** 选题 ID */
  private String topicId;
  /** 任务 ID */
  private String jobId;
  /** 状态 */
  private String status;
  /** 错误信息 */
  private String errorMessage;
  /** 创建时间 */
  private LocalDateTime createdAt;
  /** 结束时间 */
  private LocalDateTime finishedAt;
}
