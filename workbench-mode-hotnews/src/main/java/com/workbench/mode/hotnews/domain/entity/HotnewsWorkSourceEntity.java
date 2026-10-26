package com.workbench.mode.hotnews.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 热点文章来源快照行。 */
@TableName("hotnews_work_sources")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotnewsWorkSourceEntity {
  /** 主键。 */
  @TableId("id")
  private String id;
  /** 作品 ID。 */
  @TableField("work_id")
  private String workId;
  /** 顺序。 */
  @TableField("seq")
  private Integer seq;
  /** 行 ID。 */
  @TableField("row_id")
  private String rowId;
  /** 选题 ID。 */
  @TableField("topic_id")
  private String topicId;
  /** 信源条目 ID。 */
  @TableField("source_item_id")
  private String sourceItemId;
  /** 角色。 */
  @TableField("role")
  private String role;
  /** 标题。 */
  @TableField("title")
  private String title;
  /** 展示名。 */
  @TableField("name")
  private String name;
  /** 来源名。 */
  @TableField("source_name")
  private String sourceName;
  /** 来源标题。 */
  @TableField("source_title")
  private String sourceTitle;
  /** 链接。 */
  @TableField("url")
  private String url;
  /** 链接别名。 */
  @TableField("source_url")
  private String sourceUrl;
  /** 摘要。 */
  @TableField("summary")
  private String summary;
}
