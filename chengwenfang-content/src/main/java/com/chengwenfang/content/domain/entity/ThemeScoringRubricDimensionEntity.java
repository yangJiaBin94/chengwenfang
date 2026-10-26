package com.chengwenfang.content.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 主题评分细则维度实体。 */
@TableName("theme_scoring_rubric_dimensions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThemeScoringRubricDimensionEntity {
  /** 主键。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;
  /** 主题 ID。 */
  @TableField("theme_id")
  private String themeId;
  /** 画像种类。 */
  @TableField("profile_kind")
  private String profileKind;
  /** 顺序。 */
  @TableField("seq")
  private Integer seq;
  /** 维度键。 */
  @TableField("dimension_key")
  private String dimensionKey;
  /** 维度名称。 */
  @TableField("label")
  private String label;
  /** 维度说明。 */
  @TableField("description")
  private String description;
  /** 权重。 */
  @TableField("weight")
  private Integer weight;
}
