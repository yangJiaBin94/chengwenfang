package com.chengwenfang.content.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 主题评分细则实体。 */
@TableName("theme_scoring_rubrics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThemeScoringRubricEntity {
  /** 主题 ID。 */
  @TableId(value = "theme_id", type = IdType.INPUT)
  private String themeId;
  /** 画像种类。 */
  @TableField("profile_kind")
  private String profileKind;
  /** scoring_rubric.version。 */
  @TableField("rubric_version")
  private Integer rubricVersion;
  /** scoring_rubric.generatedAt。 */
  @TableField("generated_at")
  private String generatedAt;
  /** scoring_rubric.scoreGuidance。 */
  @TableField("score_guidance")
  private String scoreGuidance;
}
