package com.workbench.content.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 创建生成任务结果。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerationJobCreateView {
  /** 任务 ID。 */
  private String jobId;

  /** 任务类型。 */
  private String type;
}
