package com.chengwenfang.content.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 课程主题课表头（路线叙事与草案头）。 */
@TableName("course_curricula")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseCurriculumEntity {
  /** 主题 ID（主键）。 */
  @TableId(value = "theme_id", type = IdType.INPUT)
  private String themeId;
  /** 已提交的路线叙事。 */
  @TableField("route_narrative")
  private String routeNarrative;
  /** 课表草案 schemaVersion。 */
  @TableField("draft_schema_version")
  private Integer draftSchemaVersion;
  /** 课表草案路线叙事。 */
  @TableField("draft_route_narrative")
  private String draftRouteNarrative;
  /** 更新时间。 */
  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
