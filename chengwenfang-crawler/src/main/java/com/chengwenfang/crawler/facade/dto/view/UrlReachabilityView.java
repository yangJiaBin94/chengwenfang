package com.chengwenfang.crawler.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** URL 直连可达性探测结果。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrlReachabilityView {
  /** 是否可打开。 */
  private boolean reachable;
  /** HTTP 状态；连接失败为 0。 */
  private int httpStatus;
  /** 失败原因；成功为空。 */
  private String reason;
}
