package com.chengwenfang.content.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 课程产出学习内容同步来源。 */
@TableName("course_output_sources")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseOutputSourceEntity {
  /** 产出 ID。 */
  @TableId("output_id")
  private String outputId;
  /** 同步相对路径。 */
  @TableField("sync_relative_path")
  private String syncRelativePath;
  /** 源文件 ID。 */
  @TableField("source_file_id")
  private String sourceFileId;
}
