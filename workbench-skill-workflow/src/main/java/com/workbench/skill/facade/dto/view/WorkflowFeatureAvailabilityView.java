package com.workbench.skill.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能键对应工作流的可用性反查结果（供按钮点击门禁）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowFeatureAvailabilityView {
  /** 功能键。 */
  private String featureKey;
  /** 是否可启动相关功能。 */
  private boolean available;
  /** 工作流定义 ID；不可用时可为 null。 */
  private String workflowId;
  /** 工作流名称。 */
  private String name;
  /** 定义状态 active/inactive 等。 */
  private String status;
  /** 已发布版本 ID。 */
  private String publishedVersionId;
  /** 不可用时的中文说明；可用时可空。 */
  private String message;
}
