package com.chengwenfang.content.facade.dto.view;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 文章排版布局（对齐前端 ArticleLayoutData）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleLayoutView {
  /** 布局 ID。 */
  private String id;

  /** 文章 ID。 */
  private String articleId;

  /** 文章版本号。 */
  private Integer articleVersionNo;

  /** 主题版本 ID。 */
  private String themeVersionId;

  /** 布局选项。 */
  private Map<String, Object> options;

  /** 更新时间。 */
  private String updatedAt;
}
