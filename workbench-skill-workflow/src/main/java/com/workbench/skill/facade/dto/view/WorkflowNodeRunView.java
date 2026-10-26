package com.workbench.skill.facade.dto.view;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 工作流节点运行视图（对齐前端 WorkflowNodeData / 原版 WorkflowNodeResponse）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowNodeRunView {
  /** 节点运行 id。 */
  private String id;
  /** 节点 key。 */
  private String nodeKey;
  /** 执行序号。 */
  private Integer sequenceNo;
  /** 同节点重试序号。 */
  private Integer attemptNo;
  /** pending / running / succeeded / failed。 */
  private String status;
  /** 输入摘要。 */
  private Map<String, Object> inputSummary;
  /** 输出摘要。 */
  private Map<String, Object> outputSummary;
  /** 错误码。 */
  private String errorCode;
  /** 错误信息。 */
  private String errorMessage;
  /** 开始时间 ISO。 */
  private String startedAt;
  /** 结束时间 ISO。 */
  private String finishedAt;
}
