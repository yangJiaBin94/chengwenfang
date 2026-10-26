package com.chengwenfang.mode.hotnews.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 资料条目评分维度行。 */
@TableName("hotnews_research_item_dimensions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotnewsResearchItemDimensionEntity {
  /** 主键。 */
  @TableId("id")
  private String id;
  /** 资料条目 ID。 */
  @TableField("item_id")
  private String itemId;
  /** 顺序。 */
  @TableField("seq")
  private Integer seq;
  /** 维度 key。 */
  @TableField("dimension_key")
  private String dimensionKey;
  /** 展示名。 */
  @TableField("label")
  private String label;
  /** 权重。 */
  @TableField("weight")
  private Integer weight;
  /** 分值。 */
  @TableField("score")
  private Integer score;
  /** 理由。 */
  @TableField("reason")
  private String reason;
}
