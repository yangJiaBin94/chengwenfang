package com.workbench.content.facade.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 更新文章请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleUpdateRequest {
  /** 标题。 */
  private String title;

  /** 正文。 */
  private String content;

  /** 乐观锁版本令牌。 */
  private Long versionToken;

  /** 版本来源标记。 */
  private String source;

  /** 版本原因说明。 */
  private String reason;
}
