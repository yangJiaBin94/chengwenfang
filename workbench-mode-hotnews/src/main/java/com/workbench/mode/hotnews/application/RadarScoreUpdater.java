package com.workbench.mode.hotnews.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.mode.hotnews.domain.ChineseTextNormalizer;
import com.workbench.mode.hotnews.domain.TopicMathUtil;
import com.workbench.mode.hotnews.domain.TopicScoreDetails;
import com.workbench.mode.hotnews.domain.entity.TopicClusterEntity;
import com.workbench.mode.hotnews.facade.constant.HotNewsTopicStatuses;
import com.workbench.mode.hotnews.facade.dto.request.TopicWritingScoreApplyRequest;
import com.workbench.mode.hotnews.infrastructure.mapper.TopicClusterMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 雷达选题写作分、成簇摘要与 Top10 排名更新。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RadarScoreUpdater {

  /** 选题聚类表。 */
  private final TopicClusterMapper topicMapper;
  /** 评分明细存储。 */
  private final TopicScoreDetailsStore scoreDetailsStore;

  /**
   * 写回雷达成簇摘要（简体、截断）。
   *
   * @param summaryByTopicId 选题 ID → 摘要
   */
  @Transactional
  public void updateRadarClusterSummaries(Map<String, String> summaryByTopicId) {
    if (summaryByTopicId == null || summaryByTopicId.isEmpty()) {
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    for (Map.Entry<String, String> entry : summaryByTopicId.entrySet()) {
      if (StrUtil.isBlank(entry.getKey()) || StrUtil.isBlank(entry.getValue())) {
        continue;
      }
      TopicClusterEntity cluster = topicMapper.selectById(entry.getKey());
      if (cluster == null) {
        continue;
      }
      String summary = ChineseTextNormalizer.toSimplified(entry.getValue().trim());
      if (summary.length() > 1200) {
        summary = summary.substring(0, 1200);
      }
      cluster.setSummary(summary);
      cluster.setTranslatedSummary(summary);
      cluster.setUpdatedAt(now);
      topicMapper.updateById(cluster);
    }
  }

  /**
   * 写回 AI 写作价值评分。
   *
   * @param requests 评分结果
   * @return 成功条数
   */
  @Transactional
  public int applyWritingScores(List<TopicWritingScoreApplyRequest> requests) {
    if (requests == null || requests.isEmpty()) {
      return 0;
    }
    int updated = 0;
    LocalDateTime now = LocalDateTime.now();
    for (TopicWritingScoreApplyRequest request : requests) {
      if (request == null || StrUtil.isBlank(request.getTopicId())) {
        continue;
      }
      TopicClusterEntity cluster = topicMapper.selectById(request.getTopicId());
      if (cluster == null) {
        continue;
      }
      int writing = request.getWritingScore() == null
          ? 0
          : Math.max(0, Math.min(100, request.getWritingScore()));
      cluster.setWritingScore(writing);
      if (StrUtil.isNotBlank(request.getScoreReason())) {
        String reason = request.getScoreReason().trim();
        cluster.setScoreReason(reason.length() > 1000 ? reason.substring(0, 1000) : reason);
      }
      TopicScoreDetails details = scoreDetailsStore.read(cluster.getId());
      details.setDimensions(
          request.getDimensions() == null ? List.of() : request.getDimensions());
      details.setRubricVersion(request.getRubricVersion());
      details.setRubricSnapshot(
          request.getRubricSnapshot() == null ? List.of() : request.getRubricSnapshot());
      details.setRecommendUse(request.getRecommendUse());
      details.setRiskNote(request.getRiskNote());
      if (HotNewsTopicStatuses.DISCOVERED.equals(cluster.getStatus()) && writing >= 60) {
        cluster.setStatus(HotNewsTopicStatuses.CANDIDATE);
      }
      cluster.setUpdatedAt(now);
      topicMapper.updateById(cluster);
      scoreDetailsStore.write(cluster.getId(), details);
      updated++;
    }
    return updated;
  }

  /**
   * 按写作分降序写 radar_rank（不含日限额裁剪，由门面随后调用）。
   *
   * @param themeId      主题 ID
   * @param radarBatchId 批次 ID
   * @return min(日限额, 本批次选题数)
   */
  @Transactional
  public int markRadarTop10Ranks(String themeId, String radarBatchId) {
    if (StrUtil.isBlank(themeId) || StrUtil.isBlank(radarBatchId)) {
      return 0;
    }
    // 统一按写作分降序写 radar_rank，高分在前（不因 AI 主题素材优待）
    List<TopicClusterEntity> ordered = topicMapper.selectList(new LambdaQueryWrapper<TopicClusterEntity>()
        .eq(TopicClusterEntity::getThemeId, themeId)
        .eq(TopicClusterEntity::getRadarBatchId, radarBatchId)
        .ne(TopicClusterEntity::getStatus, HotNewsTopicStatuses.EXPIRED)
        .orderByDesc(TopicClusterEntity::getWritingScore)
        .orderByDesc(TopicClusterEntity::getUpdatedAt)
        .orderByAsc(TopicClusterEntity::getTitle)
        .orderByAsc(TopicClusterEntity::getId));
    int rank = 1;
    LocalDateTime now = LocalDateTime.now();
    for (TopicClusterEntity cluster : ordered) {
      cluster.setRadarRank(rank);
      cluster.setUpdatedAt(now);
      topicMapper.updateById(cluster);
      rank++;
    }
    return Math.min(TopicMathUtil.DAILY_TOPIC_LIMIT, ordered.size());
  }

}
