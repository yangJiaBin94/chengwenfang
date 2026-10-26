package com.workbench.mode.hotnews.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 热点文章骨架列表项。 */
@TableName("hotnews_article_skeleton_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotnewsArticleSkeletonItemEntity {
  @TableId("id")
  private String id;
  @TableField("topic_id")
  private String topicId;
  @TableField("item_type")
  private String itemType;
  @TableField("seq")
  private Integer seq;
  @TableField("content")
  private String content;
}
