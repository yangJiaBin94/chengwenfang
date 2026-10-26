package com.workbench.content.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 课程发布生命周期结果。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseLifecycleView {
  /** 课程 ID。 */
  private String courseId;

  /** 是否已发布。 */
  private boolean published;

  /** 状态。 */
  private String status;
}
