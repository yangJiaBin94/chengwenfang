package com.chengwenfang.content.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 课表草案课次实体。 */
@TableName("course_curriculum_draft_lessons")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseCurriculumDraftLessonEntity {
  /** 主键。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;
  /** 草案系列 ID。 */
  @TableField("draft_series_id")
  private String draftSeriesId;
  /** 列表顺序。 */
  @TableField("seq")
  private Integer seq;
  /** 课序号。 */
  @TableField("lesson_no")
  private Integer lessonNo;
  /** 标题。 */
  @TableField("title")
  private String title;
  /** 摘要。 */
  @TableField("summary")
  private String summary;
}
