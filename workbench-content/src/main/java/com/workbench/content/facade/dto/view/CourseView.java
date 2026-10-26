package com.workbench.content.facade.dto.view;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 课程概要视图。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseView {
  /** 课程 ID。 */
  private String id;

  /** 所属主题 ID。 */
  private String themeId;

  /** 系列 ID。 */
  private String seriesId;

  /** 系列名称。 */
  private String series;

  /** 课次文案。 */
  private String lesson;

  /** 课序号。 */
  private Integer lessonNo;

  /** 标题。 */
  private String title;

  /** 摘要。 */
  private String summary;

  /** 状态。 */
  private String status;

  /** 是否已发布。 */
  private Boolean published;

  /** 进度。 */
  private Integer progress;

  /** 原稿字数。 */
  private int draftWords;

  /** 公众号字数。 */
  private int articleWords;

  /** 配图数量。 */
  private int images;

  /** 配图资源列表。 */
  private List<CourseMediaView> imageAssets;

  /** 配图公开路径列表。 */
  private List<String> imagePublicPaths;

  /** 原稿文件路径。 */
  private String draftFilePath;

  /** 公众号文件路径。 */
  private String articleFilePath;

  /** 审核分。 */
  private int reviewScore;

  /** 更新时间。 */
  private String updatedAt;

  /** 当前原稿文章 ID（API 兼容；值等于 documentId）。 */
  private String currentDraftArticleId;

  /** 当前公众号文章 ID（API 兼容；值等于 documentId）。 */
  private String currentWechatArticleId;

  /** 当前原稿统一文档 ID。 */
  private String currentDraftDocumentId;

  /** 当前公众号统一文档 ID。 */
  private String currentWechatDocumentId;

  /** 元数据。 */
  private Map<String, Object> metadata;
}
