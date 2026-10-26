package com.workbench.publishing.facade.dto.view;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 公众号配置对外视图（不含 AppSecret 明文）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WechatMpSettingsView {
  /** 展示用账号备注名。 */
  private String accountName;
  /** 公众号 AppID。 */
  private String appId;
  /** 是否已配置 AppSecret。 */
  private boolean appSecretConfigured;
  /** 默认作者。 */
  private String defaultAuthor;
  /** 最近一次推送草稿的 media_id。 */
  private String lastDraftMediaId;
  /** 最近一次推送时间。 */
  private LocalDateTime lastPushedAt;
  /** 配置最近更新时间。 */
  private LocalDateTime updatedAt;
}
