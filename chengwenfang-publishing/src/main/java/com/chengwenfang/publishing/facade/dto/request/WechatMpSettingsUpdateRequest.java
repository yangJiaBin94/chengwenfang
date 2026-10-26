package com.chengwenfang.publishing.facade.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 更新公众号配置请求；空白 AppSecret 表示保留原值。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WechatMpSettingsUpdateRequest {
  /** 展示用账号备注名。 */
  private String accountName;
  /** 公众号 AppID。 */
  private String appId;
  /** 公众号 AppSecret；空或 blank 表示不修改已保存密钥。 */
  private String appSecret;
  /** 推送草稿时的默认作者（≤16 字）。 */
  private String defaultAuthor;
}
