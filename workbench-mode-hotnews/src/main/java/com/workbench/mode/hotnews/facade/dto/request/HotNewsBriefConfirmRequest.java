package com.workbench.mode.hotnews.facade.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 确认简报请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsBriefConfirmRequest {
  /** 确认备注 */
  private String note;
}
