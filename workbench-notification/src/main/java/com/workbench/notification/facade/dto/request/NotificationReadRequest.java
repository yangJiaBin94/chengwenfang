package com.workbench.notification.facade.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 更新通知已读状态请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationReadRequest {
  /** 是否已读 */
  private Boolean read;
}
