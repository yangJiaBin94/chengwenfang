package com.workbench.quality.facade.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 对任意正文执行确定性审核的请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditTextRequest {
  /** 待审核正文。 */
  private String text;
}
