package com.chengwenfang.job.facade.dto.view;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 任务结果摘要（状态 + 输出）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobResultView {
  /** 任务状态。 */
  private String status;
  /** 输出对象。 */
  private Map<String, Object> output;
}
