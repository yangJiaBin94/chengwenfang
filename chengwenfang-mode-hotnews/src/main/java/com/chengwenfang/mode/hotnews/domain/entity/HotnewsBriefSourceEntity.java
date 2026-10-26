package com.chengwenfang.mode.hotnews.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 简报来源快照行。 */
@TableName("hotnews_brief_sources")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotnewsBriefSourceEntity {
  @TableId("id")
  private String id;
  @TableField("brief_id")
  private String briefId;
  @TableField("seq")
  private Integer seq;
  @TableField("row_id")
  private String rowId;
  @TableField("topic_id")
  private String topicId;
  @TableField("source_item_id")
  private String sourceItemId;
  @TableField("role")
  private String role;
  @TableField("title")
  private String title;
  @TableField("name")
  private String name;
  @TableField("source_name")
  private String sourceName;
  @TableField("source_title")
  private String sourceTitle;
  @TableField("url")
  private String url;
  @TableField("source_url")
  private String sourceUrl;
  @TableField("summary")
  private String summary;
}
