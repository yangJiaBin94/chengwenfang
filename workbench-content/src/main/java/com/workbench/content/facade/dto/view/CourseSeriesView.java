package com.workbench.content.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 课程系列视图。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseSeriesView {
  private String id;
  private String themeId;
  private String topicId;
  private String name;
  private Integer sortOrder;
  private int courseCount;
}
