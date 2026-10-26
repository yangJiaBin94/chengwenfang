package com.workbench.content.facade.dto.view;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 主题课程路线视图（系列 + 课次权威顺序）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThemeRouteView {
  private String themeId;
  private String themeCode;
  private String themeName;
  private String routeNarrative;
  @Builder.Default
  private List<ThemeRouteSeriesView> series = new ArrayList<>();
}
