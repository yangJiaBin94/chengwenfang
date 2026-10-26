package com.chengwenfang.content.facade.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 主题元数据/状态补丁。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThemePatchRequest {
  private String name;
  private String description;
  /** enabled / disabled（仅已 commit 主题）。 */
  private String status;
  private Integer sortOrder;
}
