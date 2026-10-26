package com.workbench.content.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 课程模式作品扩展：一课一行。 */
@TableName("course_lessons")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseLessonEntity {
  /** 作品 ID（原 courseId）。 */
  @TableId("work_id")
  private String workId;
  /** 所属系列 ID。 */
  @TableField("series_id")
  private String seriesId;
  /** 系列内课序号。 */
  @TableField("lesson_no")
  private Integer lessonNo;
  /** 课程摘要。 */
  @TableField("summary")
  private String summary;
  /** 课程状态。 */
  @TableField("status")
  private String status;
  /** 生成/处理进度（0–100）。 */
  @TableField("progress")
  private Integer progress;
  /** 系列定位文案。 */
  @TableField("series_positioning")
  private String seriesPositioning;
}
