package com.workbench.content.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 课表草案学习成果实体。 */
@TableName("course_curriculum_draft_outcomes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseCurriculumDraftOutcomeEntity {
  /** 主键。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;
  /** 草案课 ID。 */
  @TableField("draft_lesson_id")
  private String draftLessonId;
  /** 顺序。 */
  @TableField("seq")
  private Integer seq;
  /** 学习成果内容。 */
  @TableField("content")
  private String content;
}
