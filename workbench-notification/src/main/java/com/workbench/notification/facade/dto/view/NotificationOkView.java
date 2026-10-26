package com.workbench.notification.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 简单成功响应。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationOkView {
  /** 是否成功 */
  private Boolean ok;
}
