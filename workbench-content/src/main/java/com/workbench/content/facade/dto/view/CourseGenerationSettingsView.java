package com.workbench.content.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 课程生成偏好（对齐前端 CourseGenerationSettingsData）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseGenerationSettingsView {
  /** 课程 ID。 */
  private String courseId;

  /** 字数档位。 */
  private String wordCountOption;

  /** 配图档位。 */
  private String imageCountOption;

  /** 推荐字数档位。 */
  private String recommendedWordCountOption;

  /** 推荐配图档位。 */
  private String recommendedImageCountOption;

  /** 推荐理由。 */
  private String recommendationReason;

  /** 推荐时间。 */
  private String recommendedAt;

  /** 是否来自缓存。 */
  private boolean fromCache;
}
