package com.workbench.publishing.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 渠道发布记录（表 {@code publications}）：产出与渠道账号的草稿绑定。
 */
@TableName("publications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WechatMpArticleDraftEntity {
  /** 主键 ID。 */
  @TableId("id")
  private String id;
  /** 产出 ID。 */
  @TableField("output_id")
  private String outputId;
  /** 渠道账号 ID。 */
  @TableField("account_id")
  private String accountId;
  /** 渠道侧 ID（公众号草稿 media_id）。 */
  @TableField("external_id")
  private String externalId;
  /** 最近一次成功推送时间。 */
  @TableField("pushed_at")
  private LocalDateTime pushedAt;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
  /** 最近更新时间。 */
  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
