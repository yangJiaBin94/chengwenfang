package com.chengwenfang.content.facade.dto.request;

import com.chengwenfang.common.FlexiblePayload;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 合并保存问卷 briefing；briefing 为局部或完整对象。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThemeBriefingPatchRequest {
  /** 问卷载荷（与向导侧表 briefing 深合并）。 */
  private FlexiblePayload briefing;
  /** 可选同步名称。 */
  private String name;
  /** 可选同步描述。 */
  private String description;
  /** 可选同步编码（仅 draft 可改）。 */
  private String code;
}
