package com.chengwenfang.content.facade.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * 创建作品扩展行时的入参：通用字段 + 课程/热点可选块。
 */
@Value
@Builder
public class WorkExtensionPayload {
  /** 作品 ID（= works.id）。 */
  String workId;
  /** 主题 ID。 */
  String themeId;
  /** 标题。 */
  String title;
  /** 状态。 */
  String status;
  /** 更新时间；空则实现侧取当前时间。 */
  LocalDateTime updatedAt;
  /** 课程扩展；非课程模式可空。 */
  CourseLessonExtension course;
  /** 热点扩展；非热点模式可空。 */
  HotNewsWorkExtension hotNews;

  /**
   * 课程课次与生成设置扩展。
   */
  @Value
  @Builder
  public static class CourseLessonExtension {
    /** 系列 ID。 */
    String seriesId;
    /** 课序号。 */
    Integer lessonNo;
    /** 摘要。 */
    String summary;
    /** 进度。 */
    Integer progress;
    /** 系列定位文案。 */
    String seriesPositioning;
    /** 字数档。 */
    String wordCountOption;
    /** 配图档。 */
    String imageCountOption;
    /** 推荐字数档。 */
    String recommendedWordCountOption;
    /** 推荐配图档。 */
    String recommendedImageCountOption;
    /** 推荐理由。 */
    String recommendationReason;
    /** 推荐时间。 */
    LocalDateTime recommendedAt;
    /** 偏好指纹。 */
    String preferenceFingerprint;
    /** 学习成果列表。 */
    List<String> learningOutcomes;
  }

  /**
   * 热点作品扩展。
   */
  @Value
  @Builder
  public static class HotNewsWorkExtension {
    /** 选题 ID。 */
    String topicId;
    /** 简报 ID。 */
    String briefId;
  }
}
