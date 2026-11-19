package com.chengwenfang.sourcing.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 信源沉淀池目录行（跨主题，无 theme_id）。
 */
@TableName("source_channel_catalog")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceChannelCatalogEntity {
  /** 主键。 */
  @TableId("id")
  private String id;
  /** 业务键。 */
  @TableField("code")
  private String code;
  /** 展示名。 */
  @TableField("name")
  private String name;
  /** 抓取类型。 */
  @TableField("channel_type")
  private String channelType;
  /** 抓取入口 URL。 */
  @TableField("base_url")
  private String baseUrl;
  /** 可选抓取配置 JSON。 */
  @TableField("config_json")
  private String configJson;
  /** 场景标签 JSON 数组。 */
  @TableField("tags_json")
  private String tagsJson;
  /** 是否启用。 */
  @TableField("enabled")
  private Boolean enabled;
  /** 匹配排序，越小越优先。 */
  @TableField("sort_order")
  private Integer sortOrder;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
  /** 更新时间。 */
  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
