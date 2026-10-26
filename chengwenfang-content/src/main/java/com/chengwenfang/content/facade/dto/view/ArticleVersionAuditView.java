package com.chengwenfang.content.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 版本附带的审核摘要。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleVersionAuditView {
  /** 总分。 */
  private int score;

  /** 通过条数。 */
  private int passed;

  /** 警告条数。 */
  private int warnings;

  /** 失败条数。 */
  private int failed;
}
