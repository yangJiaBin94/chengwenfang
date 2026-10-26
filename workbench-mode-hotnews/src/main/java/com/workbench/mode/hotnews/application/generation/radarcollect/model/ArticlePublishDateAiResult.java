package com.workbench.mode.hotnews.application.generation.radarcollect.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文章发布日抽取 Skill 的固定 JSON 结构。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticlePublishDateAiResult {
  /** ISO 发布/更新日期；无法判断为 null。 */
  private String publishedAt;
  /** 简短原因。 */
  private String reason;
}
