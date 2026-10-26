package com.chengwenfang.publishing.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 简单成功响应。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OkView {
  /** 是否成功。 */
  private boolean ok;
}