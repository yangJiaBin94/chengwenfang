package com.workbench.sourcing.facade.dto.request;

import com.workbench.crawler.facade.dto.request.ChannelFetchConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 主题下新建频道请求（对齐前端 createHotNewsSource）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsChannelCreateRequest {
  /** 名称 */
  private String name;
  /** 类型 */
  private String type;
  /** 基础 URL */
  private String baseUrl;
  /** 扩展配置 */
  private ChannelFetchConfig config;
}
