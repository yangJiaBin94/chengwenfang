package com.workbench.mode.hotnews.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 简报列表项。 */
@TableName("hotnews_brief_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotnewsBriefItemEntity {
  @TableId("id")
  private String id;
  @TableField("brief_id")
  private String briefId;
  @TableField("item_type")
  private String itemType;
  @TableField("seq")
  private Integer seq;
  @TableField("content")
  private String content;
}
