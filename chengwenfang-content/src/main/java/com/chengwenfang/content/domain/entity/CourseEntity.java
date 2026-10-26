package com.chengwenfang.content.domain.entity;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 课程聚合（内存投影）：由 works + course_lessons + settings + 文档指针组装，不再映射 courses 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseEntity {
  /** 作品/课程 ID。 */
  private String id;
  /** 系列 ID。 */
  private String seriesId;
  /** 主题 ID。 */
  private String themeId;
  /** 课程标题。 */
  private String title;
  /** 课程摘要。 */
  private String summary;
  /** 课程状态。 */
  private String status;
  /** 生成/处理进度（0–100）。 */
  private Integer progress;
  /** 课序号。 */
  private Integer lessonNo;
  /** 当前原稿文档 ID（sole-read content_documents）。 */
  private String currentDraftDocumentId;
  /** 当前公众号文档 ID（sole-read content_documents）。 */
  private String currentWechatDocumentId;
  /** 系列定位（原 metadata.seriesPositioning）。 */
  private String seriesPositioning;
  /** 学习成果列表；写入时配合 {@link #learningOutcomesTouched}。 */
  private List<String> learningOutcomes;
  /** 是否更新学习成果（含清空）。 */
  private boolean learningOutcomesTouched;
  /** 是否更新系列定位（含清空）。 */
  private boolean seriesPositioningTouched;
  /** 用户选定的字数档位。 */
  private String wordCountOption;
  /** 用户选定的配图数量档位。 */
  private String imageCountOption;
  /** 系统推荐的字数档位。 */
  private String recommendedWordCountOption;
  /** 系统推荐的配图数量档位。 */
  private String recommendedImageCountOption;
  /** 推荐理由。 */
  private String recommendationReason;
  /** 推荐生成时间。 */
  private LocalDateTime recommendedAt;
  /** 推荐结果指纹。 */
  private String preferenceFingerprint;
  /** 最近更新时间。 */
  private LocalDateTime updatedAt;

  /**
   * 标记学习成果已设置（即使为空列表也表示清空）。
   *
   * @param learningOutcomes 成果列表
   */
  public void touchLearningOutcomes(List<String> learningOutcomes) {
    this.learningOutcomes = learningOutcomes;
    this.learningOutcomesTouched = true;
  }

  /**
   * 标记系列定位已设置。
   *
   * @param seriesPositioning 定位文案
   */
  public void touchSeriesPositioning(String seriesPositioning) {
    this.seriesPositioning = seriesPositioning;
    this.seriesPositioningTouched = true;
  }
}
