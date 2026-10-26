package com.chengwenfang.publishing.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 渠道账号（表 {@code channel_accounts}），当前仅公众号单例。
 */
@TableName("channel_accounts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WechatMpSettingsEntity {
  /** 主键 ID。 */
  @TableId("id")
  private String id;
  /** 渠道编码（wechat_mp）。 */
  @TableField("channel_code")
  private String channelCode;
  /** 展示用账号备注名。 */
  @TableField("account_name")
  private String accountName;
  /** 公众号 AppID。 */
  @TableField("app_id")
  private String appId;
  /** 公众号 AppSecret 密文。 */
  @TableField("app_secret_ciphertext")
  private String appSecretCiphertext;
  /** 推送草稿时的默认作者。 */
  @TableField("default_author")
  private String defaultAuthor;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
  /** 最近更新时间。 */
  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
