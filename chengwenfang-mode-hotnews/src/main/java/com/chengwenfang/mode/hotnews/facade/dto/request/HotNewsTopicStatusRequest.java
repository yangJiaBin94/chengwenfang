package com.chengwenfang.mode.hotnews.facade.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 更新选题状态请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsTopicStatusRequest {
  /** 目标状态 */
  private String status;
}
