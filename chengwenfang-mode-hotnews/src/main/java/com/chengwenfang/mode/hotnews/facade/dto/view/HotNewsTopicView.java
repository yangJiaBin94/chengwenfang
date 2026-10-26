package com.chengwenfang.mode.hotnews.facade.dto.view;

import com.chengwenfang.mode.hotnews.domain.TopicScoreDetails;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 热点选题视图（对齐前端 HotNewsTopic）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsTopicView {
  /** 选题 ID */
  private String id;
  /** 主题 ID */
  private String themeId;
  /** 标题 */
  private String title;
  /** 摘要 */
  private String summary;
  /** 状态 */
  private String status;
  /** 热度分 */
  private Integer heatScore;
  /** 可写性分 */
  private Integer writingScore;
  /** 置信度分 */
  private Integer confidenceScore;
  /** 速度分 */
  private Integer velocityScore;
  /** 新鲜度分 */
  private Integer freshnessScore;
  /** 证据分 */
  private Integer evidenceScore;
  /** 生命周期 */
  private String lifecycle;
  /** 评分明细 */
  private TopicScoreDetails scoreDetails;
  /** 评分理由 */
  private String scoreReason;
  /** 来源数量 */
  private Long sourceCount;
  /** 首次出现时间 */
  private LocalDateTime firstSeenAt;
  /** 最近出现时间 */
  private LocalDateTime lastSeenAt;
  /** 成员信源最早发布时间；均未知则为 null */
  private LocalDateTime publishFrom;
  /** 成员信源最晚发布时间；均未知则为 null */
  private LocalDateTime publishTo;
  /** 过期时间 */
  private LocalDateTime expiresAt;
  /** 创建时间 */
  private LocalDateTime createdAt;
  /** 更新时间 */
  private LocalDateTime updatedAt;
  /** 确认选题时间 */
  private LocalDateTime confirmedAt;
  /** 雷达采集批次 ID */
  private String radarBatchId;
  /** 本批次名次 */
  private Integer radarRank;
  /** 成员来源站标签（去重） */
  private java.util.List<String> siteLabels;
  /** 成员媒体类型标签（去重） */
  private java.util.List<String> mediaTypes;
  /**
   * 选题来源形态（如 ai_theme_seed）；与 scoreDetails.originKind 一致，便于列表排序。
   */
  private String originKind;
}
