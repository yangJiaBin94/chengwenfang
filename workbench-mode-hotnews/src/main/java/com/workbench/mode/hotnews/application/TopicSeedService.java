package com.workbench.mode.hotnews.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.common.IdsUtil;
import com.workbench.mode.hotnews.domain.ChineseTextNormalizer;
import com.workbench.mode.hotnews.domain.TopicScoreDetails;
import com.workbench.mode.hotnews.domain.entity.TopicClusterEntity;
import com.workbench.mode.hotnews.facade.constant.HotNewsTopicOriginKinds;
import com.workbench.mode.hotnews.facade.constant.HotNewsTopicStatuses;
import com.workbench.mode.hotnews.facade.dto.request.AiThemeSeedPersistRequest;
import com.workbench.mode.hotnews.facade.dto.view.HotNewsTopicView;
import com.workbench.mode.hotnews.infrastructure.mapper.TopicClusterMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * AI 主题种子选题落库与同批次同标题幂等查找。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TopicSeedService {

  /** 选题生命周期缺省值。 */
  private static final String DEFAULT_LIFECYCLE_EARLY = "early";

  /** 选题簇 Mapper。 */
  private final TopicClusterMapper topicMapper;
  /** 评分明细存储。 */
  private final TopicScoreDetailsStore scoreDetailsStore;
  /** 选题查询（视图组装）。 */
  private final TopicQueryService query;

  public HotNewsTopicView persistAiThemeSeedTopic(
      String themeId, String radarBatchId, AiThemeSeedPersistRequest request) {
    if (StrUtil.isBlank(themeId) || StrUtil.isBlank(radarBatchId) || request == null) {
      return null;
    }
    String title = ChineseTextNormalizer.toSimplified(StrUtil.trim(StrUtil.nullToEmpty(request.getTitle())));
    String summary = ChineseTextNormalizer.toSimplified(
        StrUtil.trim(StrUtil.nullToEmpty(request.getSummary())));
    if (StrUtil.isBlank(title) || StrUtil.isBlank(summary)) {
      return null;
    }
    if (title.length() > 200) {
      title = title.substring(0, 200);
    }
    if (summary.length() > 1200) {
      summary = summary.substring(0, 1200);
    }
    // 同批次同标题幂等：复用已有 AI 种子
    TopicClusterEntity existing = findAiThemeSeedByTitle(themeId, radarBatchId, title);
    if (existing != null) {
      return query.topicView(existing);
    }
    LocalDateTime now = LocalDateTime.now();
    List<String> signals = new ArrayList<>();
    signals.add(HotNewsTopicOriginKinds.AI_THEME_SEED_LABEL);
    if (request.getSignals() != null) {
      for (String signal : request.getSignals()) {
        String text = StrUtil.trim(StrUtil.nullToEmpty(signal));
        if (StrUtil.isBlank(text) || signals.contains(text)) {
          continue;
        }
        signals.add(text);
        if (signals.size() >= 4) {
          break;
        }
      }
    }
    String angle = StrUtil.trim(StrUtil.nullToEmpty(request.getAngle()));
    String riskNote = "无外链信源，需人工核实后再确认选题";
    if (StrUtil.isNotBlank(angle)) {
      riskNote = riskNote + "；推荐切口：" + (angle.length() > 40 ? angle.substring(0, 40) : angle);
    }
    TopicScoreDetails details = TopicScoreDetails.builder()
        .sourceCount(0)
        .sourceTypes(List.of(HotNewsTopicOriginKinds.AI_THEME_SEED))
        .signals(signals)
        .metricNote("主题问卷 AI 种子素材，待主题量表评分")
        .riskNote(riskNote)
        .originKind(HotNewsTopicOriginKinds.AI_THEME_SEED)
        .build();
    TopicClusterEntity cluster = TopicClusterEntity.builder()
        .id(IdsUtil.newId())
        .themeId(themeId)
        .title(title)
        .summary(summary)
        .translatedTitle(title)
        .translatedSummary(summary)
        .status(HotNewsTopicStatuses.DISCOVERED)
        .heatScore(40)
        .writingScore(0)
        .confidenceScore(35)
        .velocityScore(40)
        .freshnessScore(100)
        .evidenceScore(20)
        .lifecycle(DEFAULT_LIFECYCLE_EARLY)
        .scoreReason("AI 主题种子素材，写作价值待主题量表评分")
        .firstSeenAt(now)
        .lastSeenAt(now)
        .createdAt(now)
        .updatedAt(now)
        .radarBatchId(radarBatchId)
        .build();
    topicMapper.insert(cluster);
    scoreDetailsStore.write(cluster.getId(), details);
    log.info(
        "AI 主题种子素材已落库 themeId={} batch={} topicId={}",
        themeId,
        radarBatchId,
        cluster.getId());
    return query.topicView(cluster);
  }

  /**
   * 按规范化标题查找本批次已存在的 AI 主题种子选题。
   *
   * @param themeId      主题 ID
   * @param radarBatchId 批次 ID
   * @param title        已规范化标题
   * @return 实体；无则 null
   */
  private TopicClusterEntity findAiThemeSeedByTitle(
      String themeId, String radarBatchId, String title) {
    if (StrUtil.isBlank(title)) {
      return null;
    }
    List<TopicClusterEntity> clusters = topicMapper.selectList(new LambdaQueryWrapper<TopicClusterEntity>()
        .eq(TopicClusterEntity::getThemeId, themeId)
        .eq(TopicClusterEntity::getRadarBatchId, radarBatchId)
        .ne(TopicClusterEntity::getStatus, HotNewsTopicStatuses.EXPIRED));
    for (TopicClusterEntity cluster : clusters) {
      if (cluster == null) {
        continue;
      }
      TopicScoreDetails details = scoreDetailsStore.read(cluster.getId());
      if (!HotNewsTopicOriginKinds.isAiThemeSeed(details.getOriginKind())) {
        continue;
      }
      String existingTitle = ChineseTextNormalizer.toSimplified(
          StrUtil.trim(StrUtil.nullToEmpty(cluster.getTitle())));
      if (title.equals(existingTitle)) {
        return cluster;
      }
    }
    return null;
  }


}
