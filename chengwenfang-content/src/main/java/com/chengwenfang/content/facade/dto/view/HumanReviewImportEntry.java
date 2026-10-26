package com.chengwenfang.content.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量导入行项目（*Entry），非普通资源 View；按类命名词典冻结。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HumanReviewImportEntry {
  /** 课程 ID。 */
  private String courseId;

  /** 状态。 */
  private String status;

  /** 评论。 */
  private String comment;
}
