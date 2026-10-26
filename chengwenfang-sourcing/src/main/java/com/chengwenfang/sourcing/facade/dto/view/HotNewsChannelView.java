package com.chengwenfang.sourcing.facade.dto.view;

import com.chengwenfang.crawler.facade.dto.request.ChannelFetchConfig;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 热点信源频道视图（对齐前端 HotNewsSource）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsChannelView {
  /** 频道 ID */
  private String id;
  /** 主题 ID */
  private String themeId;
  /** 名称 */
  private String name;
  /** 类型：rss / web / github */
  private String type;
  /** 基础 URL */
  private String baseUrl;
  /** 是否启用 */
  private Boolean enabled;
  /** 扩展配置（apiKey 已脱敏或不含明文） */
  private ChannelFetchConfig config;
  /** 是否已配置凭证 */
  private Boolean credentialConfigured;
  /** 最近成功时间 */
  private LocalDateTime lastSuccessAt;
  /** 软删除时间 */
  private LocalDateTime deletedAt;
  /** 创建时间 */
  private LocalDateTime createdAt;
  /** 更新时间 */
  private LocalDateTime updatedAt;
}
