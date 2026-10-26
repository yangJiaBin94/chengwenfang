package com.workbench.skill.facade.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 发布工作流请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowPublishRequest {
  /** 版本号标签。 */
  private String version;
  /** 发布说明。 */
  private String releaseNotes;
}
