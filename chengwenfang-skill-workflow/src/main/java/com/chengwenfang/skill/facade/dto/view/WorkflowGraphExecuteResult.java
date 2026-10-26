package com.chengwenfang.skill.facade.dto.view;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 图执行结果（写入 Job 产物）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowGraphExecuteResult {
  /** 运行状态。 */
  private String status;
  /** 错误信息。 */
  private String errorMessage;
  /** 图引擎完整输出。 */
  private Map<String, Object> payload;

  /** 展开为 Job 输出 Map。 */
  public Map<String, Object> toJobOutput() {
    if (payload != null && !payload.isEmpty()) {
      return new LinkedHashMap<>(payload);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    if (status != null) {
      out.put("status", status);
    }
    if (errorMessage != null) {
      out.put("errorMessage", errorMessage);
    }
    return out;
  }
}
