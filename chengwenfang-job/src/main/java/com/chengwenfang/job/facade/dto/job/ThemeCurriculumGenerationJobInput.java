package com.chengwenfang.job.facade.dto.job;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@link com.chengwenfang.job.facade.constant.JobTypes#THEME_CURRICULUM_GENERATION} 入参。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThemeCurriculumGenerationJobInput {
  /** 主题 ID。 */
  private String themeId;
}
