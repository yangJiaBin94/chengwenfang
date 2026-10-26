package com.chengwenfang.job.facade.dto.job;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@link com.chengwenfang.job.facade.constant.JobTypes#HOT_NEWS_SOURCE_VIDEO_PARSE} 入参。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsSourceVideoParseJobInput {
  /** 主题 ID。 */
  private String themeId;
}
