package com.workbench.content.facade.dto.request;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 保存排版布局请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleLayoutSaveRequest {
  /** 统一文档 id；空则服务端按 articleId 解析。 */
  private String documentId;

  /** 文章版本号。 */
  private Integer articleVersionNo;

  /** 主题版本 ID。 */
  private String themeVersionId;

  /** 布局选项。 */
  private Map<String, Object> options;
}
