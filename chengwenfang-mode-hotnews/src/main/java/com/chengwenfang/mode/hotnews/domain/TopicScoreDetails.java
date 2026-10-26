package com.chengwenfang.mode.hotnews.domain;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 选题聚类评分明细（写入 score_details_json / 对外 scoreDetails）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicScoreDetails {
  /** 来源总数。 */
  private int sourceCount;
  /** 官方级来源数。 */
  private int officialCount;
  /** 一手来源数。 */
  private int primaryCount;
  /** 二手/聚合来源数。 */
  private int secondaryCount;
  /** 社区来源数。 */
  private int communityCount;
  /** 缺发布时间条数。 */
  private int unknownDateCount;
  /** 推断的来源种类。 */
  @Builder.Default
  private List<String> sourceTypes = new ArrayList<>();
  /** 独立来源数。 */
  private int independentSourceCount;
  /** 近 24h 新增来源数。 */
  private int recentSourceCount;
  /** AI 相关词命中。 */
  private int aiHits;
  /** 深度信号命中。 */
  private int depthHits;
  /** 近 24h 增长占比。 */
  private double sourceGrowthRatio;
  /** 最近见到距今小时。 */
  private double freshnessHours;
  /** 可读信号文案。 */
  @Builder.Default
  private List<String> signals = new ArrayList<>();
  /** 指标说明。 */
  private String metricNote;
  /** AI 分维结果（按主题量表）。 */
  @Builder.Default
  private List<TopicDimensionScore> dimensions = new ArrayList<>();
  /** 评分时量表版本快照。 */
  private Integer rubricVersion;
  /** 评分时量表维度快照（解释历史分用）。 */
  @Builder.Default
  private List<TopicDimensionScore> rubricSnapshot = new ArrayList<>();
  /** 推荐用法：lead / brief / watch / skip。 */
  private String recommendUse;
  /** 风险提示（可空）。 */
  private String riskNote;
  /**
   * 选题来源形态（如 {@code ai_theme_seed}）；普通渠道聚合可空。
   */
  private String originKind;
}
