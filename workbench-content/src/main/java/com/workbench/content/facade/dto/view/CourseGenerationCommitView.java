package com.workbench.content.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 课程生成提交结果。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseGenerationCommitView {
  /** 课程 ID。 */
  private String courseId;

  /** 原稿文章。 */
  private ArticleView draft;

  /** 公众号文章。 */
  private ArticleView wechat;

  /** 是否部分成功。 */
  private boolean partial;
}
