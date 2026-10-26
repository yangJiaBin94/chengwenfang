package com.chengwenfang.content.facade.dto.view;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 路线中的系列块。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThemeRouteSeriesView {
  private String id;
  private String name;
  private Integer sortOrder;
  private String positioning;
  @Builder.Default
  private List<CourseView> lessons = new ArrayList<>();
}
