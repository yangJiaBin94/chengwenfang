package com.workbench.content.facade.dto.view;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 系列前后课与路线。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeriesNeighborsView {
  /** 上一课。 */
  private SeriesNeighborItemView previous;

  /** 下一课。 */
  private SeriesNeighborItemView following;

  /** 完整课程序列。 */
  private List<SeriesNeighborItemView> route;
}
