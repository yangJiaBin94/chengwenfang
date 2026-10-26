package com.workbench.mode.hotnews.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 选题评分明细标量。 */
@TableName("hotnews_topic_score_metrics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotnewsTopicScoreMetricEntity {
  @TableId("topic_id")
  private String topicId;
  @TableField("source_count")
  private Integer sourceCount;
  @TableField("official_count")
  private Integer officialCount;
  @TableField("primary_count")
  private Integer primaryCount;
  @TableField("secondary_count")
  private Integer secondaryCount;
  @TableField("community_count")
  private Integer communityCount;
  @TableField("unknown_date_count")
  private Integer unknownDateCount;
  @TableField("independent_source_count")
  private Integer independentSourceCount;
  @TableField("recent_source_count")
  private Integer recentSourceCount;
  @TableField("ai_hits")
  private Integer aiHits;
  @TableField("depth_hits")
  private Integer depthHits;
  @TableField("source_growth_ratio")
  private Double sourceGrowthRatio;
  @TableField("freshness_hours")
  private Double freshnessHours;
  @TableField("metric_note")
  private String metricNote;
  @TableField("rubric_version")
  private Integer rubricVersion;
  @TableField("recommend_use")
  private String recommendUse;
  @TableField("risk_note")
  private String riskNote;
  @TableField("origin_kind")
  private String originKind;
}
