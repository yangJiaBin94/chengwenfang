package com.workbench.publishing.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 公众号凭证连通性探测结果。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WechatMpConnectionTestResult {
  /** 是否成功。 */
  private boolean ok;
  /** 可读说明（成功预览或失败原因）。 */
  private String message;
}
