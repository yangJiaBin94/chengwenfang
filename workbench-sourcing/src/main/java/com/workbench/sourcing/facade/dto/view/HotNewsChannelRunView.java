package com.workbench.sourcing.facade.dto.view;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 抓取运行中的频道子运行视图。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsChannelRunView {
  /** 子运行 ID */
  private String id;
  /** 频道 ID */
  private String channelId;
  /** 频道名称 */
  private String channelName;
  /** 频道类型 */
  private String channelType;
  /** 状态 */
  private String status;
  /** 条目数 */
  private Integer itemsCount;
  /** 新增条目数 */
  private Integer newItems;
  /** 错误码 */
  private String errorCode;
  /** 错误信息 */
  private String errorMessage;
  /** 开始时间 */
  private LocalDateTime startedAt;
  /** 结束时间 */
  private LocalDateTime finishedAt;
}
