package com.workbench.content.facade.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 保存课程生成偏好请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseGenerationSettingsRequest {
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
}
