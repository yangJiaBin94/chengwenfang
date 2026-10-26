package com.chengwenfang.content.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 课表生成任务创建结果。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThemeCurriculumGenerateView {
  private String themeId;
  private String jobId;
  private String status;
}
