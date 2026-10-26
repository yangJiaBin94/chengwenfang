package com.workbench.content.facade.dto.request;

import com.workbench.common.FlexiblePayload;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 创建主题草稿请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThemeDraftCreateRequest {
  /** 可选初始名称。 */
  private String name;
  /** 可选初始描述。 */
  private String description;
  /** 可选初始编码；空则后端生成。 */
  private String code;
  /** 可选初始问卷片段。 */
  private FlexiblePayload briefing;
}
