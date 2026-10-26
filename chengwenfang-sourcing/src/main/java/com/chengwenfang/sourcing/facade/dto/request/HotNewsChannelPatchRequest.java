package com.chengwenfang.sourcing.facade.dto.request;

import com.chengwenfang.crawler.facade.dto.request.ChannelFetchConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 频道局部更新请求（对齐前端 patchHotNewsSource）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsChannelPatchRequest {
  /** 名称 */
  private String name;
  /** 类型 */
  private String type;
  /** 基础 URL */
  private String baseUrl;
  /** 是否启用 */
  private Boolean enabled;
  /** 扩展配置 */
  private ChannelFetchConfig config;
}
