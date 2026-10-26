package com.workbench.content.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 课的学习成果行。 */
@TableName("course_lesson_outcomes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseLessonOutcomeEntity {
  /** 主键。 */
  @TableId("id")
  private String id;
  /** 作品 ID。 */
  @TableField("work_id")
  private String workId;
  /** 顺序。 */
  @TableField("seq")
  private Integer seq;
  /** 学习成果正文。 */
  @TableField("content")
  private String content;
}
