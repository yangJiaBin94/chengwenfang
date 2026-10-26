package com.workbench.mode.hotnews.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 简报/文章来源快照中的单行（持久化为 JSON 数组元素）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsBriefSourceRow {
  /** 行 ID。 */
  private String id;
  /** 选题 ID。 */
  private String topicId;
  /** 来源条目或资料池条目 ID。 */
  private String sourceItemId;
  /** 角色（如 evidence）。 */
  private String role;
  /** 标题。 */
  private String title;
  /** 来源展示名（别名 name）。 */
  private String name;
  /** 来源名。 */
  private String sourceName;
  /** 来源标题（与 title 同口径别名）。 */
  private String sourceTitle;
  /** 链接。 */
  private String url;
  /** 链接别名。 */
  private String sourceUrl;
  /** 摘要。 */
  private String summary;
}
