package com.workbench.sourcing.facade.dto.view;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 频道软删除结果（对齐前端 deleteHotNewsSource）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsChannelDeleteView {
  /** 频道 ID */
  private String id;
  /** 删除时间 */
  private LocalDateTime deletedAt;
}
