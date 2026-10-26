package com.workbench.skill.facade.dto.view;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 节点类型目录项。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowNodeTypeView {
  /** 类型 key。 */
  private String key;
  /** 名称。 */
  private String name;
  /** 描述。 */
  private String description;
  /** 分类。 */
  private String category;
  /** 模型类型：none / text / image。 */
  private String modelType;
  /** 执行器版本。 */
  private String executorVersion;
  /** 可配置级别：A / B / C / D。 */
  private String configurability;
  /** 是否支持重试。 */
  private Boolean supportsRetry;
  /** 是否有副作用。 */
  private Boolean sideEffects;
  /** 是否纳入基线。 */
  private Boolean includeInBaseline;
  /** 配置 Schema。 */
  private Map<String, Object> configSchema;
  /** 输入 Schema。 */
  private Map<String, Object> inputSchema;
  /** 输出 Schema。 */
  private Map<String, Object> outputSchema;
}
