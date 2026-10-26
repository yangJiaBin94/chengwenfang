package com.workbench.sourcing.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 搜索词 × 网站主题级拉黑。 */
@TableName("source_query_site_blocks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceQuerySiteBlockEntity {
  /** 主键。 */
  @TableId("id")
  private String id;
  /** 主题 ID。 */
  @TableField("theme_id")
  private String themeId;
  /** 渠道类型（如 searxng）。 */
  @TableField("channel_type")
  private String channelType;
  /** 搜索词。 */
  @TableField("search_query")
  private String searchQuery;
  /** 站点键（注册域）。 */
  @TableField("site_key")
  private String siteKey;
  /** 状态：active / revoked。 */
  @TableField("status")
  private String status;
  /** 累计命中偏题次数。 */
  @TableField("hit_count")
  private Integer hitCount;
  /** 拉黑原因摘要。 */
  @TableField("reason")
  private String reason;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
  /** 更新时间。 */
  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
