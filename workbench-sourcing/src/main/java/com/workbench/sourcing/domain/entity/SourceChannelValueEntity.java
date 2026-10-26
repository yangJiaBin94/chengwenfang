package com.workbench.sourcing.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 信源频道配置中的列表值（queries / urls）。 */
@TableName("source_channel_values")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceChannelValueEntity {
  /** 主键。 */
  @TableId("id")
  private String id;
  /** 频道 ID。 */
  @TableField("channel_id")
  private String channelId;
  /** 值类型：query / url。 */
  @TableField("value_type")
  private String valueType;
  /** 顺序。 */
  @TableField("seq")
  private Integer seq;
  /** 值。 */
  @TableField("value")
  private String value;
}
