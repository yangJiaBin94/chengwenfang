package com.workbench.sourcing.facade.dto.view;

import com.workbench.crawler.facade.dto.request.ChannelFetchConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 抓取用频道引用（含完整 config，供 Worker 使用，勿直接对外暴露未脱敏字段）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelView {
  /** 频道 ID。 */
  private String id;
  /** 名称。 */
  private String name;
  /** 类型。 */
  private String type;
  /** 基础 URL。 */
  private String baseUrl;
  /** 扩展配置（可含 apiKey 明文，仅服务端内部传递）。 */
  private ChannelFetchConfig config;
}
