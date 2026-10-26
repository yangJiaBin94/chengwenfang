package com.chengwenfang.publishing.facade.dto.request;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 创建/更新排版主题请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LayoutThemeSaveRequest {
  /** 名称。 */
  private String name;
  /** 描述。 */
  private String description;
  /** 主题配置。 */
  private Map<String, Object> config;
}