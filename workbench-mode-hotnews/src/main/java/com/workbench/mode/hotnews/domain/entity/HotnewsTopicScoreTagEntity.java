package com.workbench.mode.hotnews.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 选题评分标签行。 */
@TableName("hotnews_topic_score_tags")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotnewsTopicScoreTagEntity {
  @TableId("id")
  private String id;
  @TableField("topic_id")
  private String topicId;
  @TableField("tag_type")
  private String tagType;
  @TableField("seq")
  private Integer seq;
  @TableField("tag_value")
  private String tagValue;
}
