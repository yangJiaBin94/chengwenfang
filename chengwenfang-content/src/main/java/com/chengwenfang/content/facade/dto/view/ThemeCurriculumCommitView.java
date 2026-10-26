package com.chengwenfang.content.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 课表 commit 结果。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThemeCurriculumCommitView {
  private String themeId;
  private String status;
  private int seriesCount;
  private int courseCount;
}
