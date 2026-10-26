package com.workbench.sourcing.facade.dto.view;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 频道连通性探测结果（对齐前端 HotNewsSourceTest）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsChannelTestView {
  /** 是否成功（兼容字段） */
  private Boolean ok;
  /** 是否成功 */
  private Boolean success;
  /** 样例条目数 */
  private Integer itemCount;
  /** 提示信息 */
  private String message;
  /** 错误码 */
  private String errorCode;
  /** 错误信息 */
  private String errorMessage;
  /** 探测时间 */
  private LocalDateTime checkedAt;
}
