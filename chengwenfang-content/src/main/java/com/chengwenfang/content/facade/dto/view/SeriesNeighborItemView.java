package com.chengwenfang.content.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 系列课邻居项。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeriesNeighborItemView {
  /** 课程 ID。 */
  private String id;

  /** 标题。 */
  private String title;

  /** 摘要。 */
  private String summary;

  /** 课序号。 */
  private Integer lessonNo;
}
