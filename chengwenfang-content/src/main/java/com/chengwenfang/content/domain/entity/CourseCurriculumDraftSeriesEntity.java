package com.chengwenfang.content.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 课表草案系列实体。 */
@TableName("course_curriculum_draft_series")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseCurriculumDraftSeriesEntity {
  /** 主键。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;
  /** 主题 ID。 */
  @TableField("theme_id")
  private String themeId;
  /** 列表顺序。 */
  @TableField("seq")
  private Integer seq;
  /** 草案临时 ID。 */
  @TableField("temp_id")
  private String tempId;
  /** 系列名。 */
  @TableField("name")
  private String name;
  /** 原 sortOrder。 */
  @TableField("sort_order")
  private Integer sortOrder;
  /** 系列定位。 */
  @TableField("positioning")
  private String positioning;
}
