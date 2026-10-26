package com.chengwenfang.publishing.facade.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 主题启用状态更新请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThemeStatusUpdateRequest {
  /** 状态 enabled / disabled。 */
  private String status;
}