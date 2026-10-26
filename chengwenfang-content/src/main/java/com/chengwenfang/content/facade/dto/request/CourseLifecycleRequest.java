package com.chengwenfang.content.facade.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 更新课程生命周期请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseLifecycleRequest {
  /** 是否发布。 */
  private Boolean published;
}
