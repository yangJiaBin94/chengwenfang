package com.chengwenfang.content.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 课程系列（主题下的课程序列分组）。 */
@TableName("course_series")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseSeriesEntity {
  /** 主键 ID。 */
  @TableId("id")
  private String id;
  /** 主题 ID。 */
  @TableField("theme_id")
  private String themeId;
  /** 系列名称。 */
  @TableField("name")
  private String name;
  /** 排序序号。 */
  @TableField("sort_order")
  private Integer sortOrder;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
  /** 最近更新时间。 */
  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
