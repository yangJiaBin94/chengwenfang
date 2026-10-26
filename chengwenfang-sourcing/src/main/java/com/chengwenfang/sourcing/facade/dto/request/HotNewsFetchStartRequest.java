package com.chengwenfang.sourcing.facade.dto.request;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 启动抓取请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsFetchStartRequest {
  /** 主题 ID */
  private String themeId;
  /** 频道 ID 列表 */
  private List<String> channelIds;
  /** 回溯小时数 */
  private Integer hours;
}
