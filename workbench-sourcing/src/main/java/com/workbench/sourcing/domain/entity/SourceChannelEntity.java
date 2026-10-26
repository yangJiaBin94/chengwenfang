package com.workbench.sourcing.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 热点信源频道（RSS/网页等）。 */
@TableName("source_channels")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceChannelEntity {
  /** 主键 ID。 */
  @TableId("id")
  private String id;
  /** 主题 ID。 */
  @TableField("theme_id")
  private String themeId;
  /** 频道名称。 */
  @TableField("name")
  private String name;
  /** 抓取类型：rss / web 等。 */
  @TableField("channel_type")
  private String channelType;
  /** 上游 Base URL。 */
  @TableField("base_url")
  private String baseUrl;
  /** 历史信任等级列；业务已废弃，落库固定 0，可信度改由 AI 评分判断。 */
  @TableField("trust_level")
  private Integer trustLevel;
  /** 是否启用。 */
  @TableField("enabled")
  private Boolean enabled;
  /** 最近一次采集成功时间。 */
  @TableField("last_success_at")
  private LocalDateTime lastSuccessAt;
  /** 是否有抓取配置。 */
  @TableField("has_config")
  private Boolean hasConfig;
  /** config.query。 */
  @TableField("primary_query")
  private String primaryQuery;
  /** config.maxResults。 */
  @TableField("max_results")
  private Integer maxResults;
  /** config.topic。 */
  @TableField("search_topic")
  private String searchTopic;
  /** config.searchDepth。 */
  @TableField("search_depth")
  private String searchDepth;
  /** config.apiKey 密文。 */
  @TableField("api_key_ciphertext")
  private String apiKeyCiphertext;
  /** config.dateField。 */
  @TableField("date_field")
  private String dateField;
  /** config.sort。 */
  @TableField("sort_mode")
  private String sortMode;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
  /** 更新时间。 */
  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
