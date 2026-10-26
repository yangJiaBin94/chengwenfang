package com.workbench.mode.hotnews.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 选题评分维度行。 */
@TableName("hotnews_topic_score_dimensions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotnewsTopicScoreDimensionEntity {
  @TableId("id")
  private String id;
  @TableField("topic_id")
  private String topicId;
  @TableField("dimension_set")
  private String dimensionSet;
  @TableField("seq")
  private Integer seq;
  @TableField("dimension_key")
  private String dimensionKey;
  @TableField("label")
  private String label;
  @TableField("weight")
  private Integer weight;
  @TableField("score")
  private Integer score;
  @TableField("reason")
  private String reason;
}
