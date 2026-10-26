package com.chengwenfang.content.facade.dto.view;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 课程正文与媒资（对齐前端 CourseContentData）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseContentView {
  /** 课程 ID。 */
  private String courseId;

  /** 原稿文章 ID。 */
  private String draftArticleId;

  /** 公众号文章 ID。 */
  private String articleArticleId;

  /** 原稿统一文档 ID。 */
  private String draftDocumentId;

  /** 公众号统一文档 ID。 */
  private String articleDocumentId;

  /** 原稿文件 ID。 */
  private String draftFileId;

  /** 公众号文件 ID。 */
  private String articleFileId;

  /** 原稿文件路径。 */
  private String draftFilePath;

  /** 公众号文件路径。 */
  private String articleFilePath;

  /** 原稿正文。 */
  private String draft;

  /** 公众号正文。 */
  private String article;

  /** 配图列表。 */
  private List<CourseMediaView> images;
}
