package com.chengwenfang.content.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 课程媒资视图（对齐前端 CourseMediaAsset）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseMediaView {
  /** 媒资 ID。 */
  private String id;

  /** 课程 ID。 */
  private String courseId;

  /** 文章 ID。 */
  private String articleId;

  /** 文件 ID。 */
  private String fileId;

  /** 类型。 */
  private String kind;

  /** 显示名称。 */
  private String name;

  /** 访问路径。 */
  private String path;

  /** 访问 URL。 */
  private String url;

  /** 源对象键。 */
  private String sourcePath;

  /** 历史 public 路径。 */
  private String publicPath;

  /** 序号。 */
  private Integer sequenceNo;
}
