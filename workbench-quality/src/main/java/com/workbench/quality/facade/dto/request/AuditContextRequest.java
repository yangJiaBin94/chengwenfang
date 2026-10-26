package com.workbench.quality.facade.dto.request;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 课程/公众号文章审核上下文请求。
 * <p>除正文外可携带配图资产，供 IMAGE_ASSETS_* 等规则使用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditContextRequest {
  /** 公众号正文 Markdown。 */
  private String article;
  /** 课程原稿（可选，用于对照）。 */
  private String draft;
  /**
   * 结构化配图资产；元素常用键：name / path / articleId / articlePath。
   */
  private List<Map<String, Object>> imageAssets;
  /** 页面同步到的公开图片路径。 */
  private List<String> imagePublicPaths;
  /** 当前正文是否已绑定数据库版本。 */
  private Boolean currentVersionSaved;
  /** 文章文件路径（文件型文章才校验 asset.articlePath）。 */
  private String articleFilePath;
}
