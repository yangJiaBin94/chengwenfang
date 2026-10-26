package com.workbench.mode.hotnews.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.common.IdsUtil;
import com.workbench.common.ThemeTitleRelevance;
import com.workbench.crawler.facade.MarketingPageFilter;
import com.workbench.crawler.facade.PublishTimeUtil;
import com.workbench.mode.hotnews.domain.ChineseTextNormalizer;
import com.workbench.mode.hotnews.domain.TopicMathUtil;
import com.workbench.mode.hotnews.domain.TopicScoreDetails;
import com.workbench.sourcing.domain.entity.SourceItemEntity;
import com.workbench.mode.hotnews.domain.entity.TopicClusterEntity;
import com.workbench.mode.hotnews.domain.entity.TopicSourceEntity;
import com.workbench.mode.hotnews.facade.constant.HotNewsTopicStatuses;
import com.workbench.sourcing.facade.enums.SourceLifecycleStatus;
import com.workbench.sourcing.infrastructure.mapper.SourceItemMapper;
import com.workbench.mode.hotnews.infrastructure.mapper.TopicClusterMapper;
import com.workbench.mode.hotnews.infrastructure.mapper.TopicSourceMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * 热点选题聚合：信源聚类、日限额、评分补算与簇摘要回填。
 * <p>由 {@link HotNewsTopicService} 薄委托；主题相关性经 {@link HotNewsTopicService#themeRelevanceProfile}。
 */
@Slf4j
@Service
public class HotNewsTopicAggregateService {

  /** 从 expired 恢复为 candidate 的写作分阈值。 */
  private static final int CANDIDATE_WRITING_SCORE_FLOOR = 60;

  /** 证据链角色：佐证。 */
  private static final String ROLE_EVIDENCE = "evidence";

  private final TopicClusterMapper topicMapper;
  private final TopicSourceMapper topicSourceMapper;
  private final SourceItemMapper itemMapper;
  private final TopicScoreDetailsStore scoreDetailsStore;
  private final HotNewsTopicService topics;

  /**
   * @param topicMapper         选题簇 Mapper
   * @param topicSourceMapper   选题-信源关联 Mapper
   * @param itemMapper          信源条目 Mapper
   * @param scoreDetailsStore   评分明细存储
   * @param topics              选题查询（@Lazy 破环）；用于主题相关性画像
   */
  public HotNewsTopicAggregateService(
      TopicClusterMapper topicMapper,
      TopicSourceMapper topicSourceMapper,
      SourceItemMapper itemMapper,
      TopicScoreDetailsStore scoreDetailsStore,
      @Lazy HotNewsTopicService topics) {
    this.topicMapper = topicMapper;
    this.topicSourceMapper = topicSourceMapper;
    this.itemMapper = itemMapper;
    this.scoreDetailsStore = scoreDetailsStore;
    this.topics = topics;
  }

  /**
   * 将近 7 日信源按标题相似度并入选题簇，重算热度分并执行日限额。
   *
   * @param themeId 主题 ID
   * @return 参与聚合的簇数量
   */
  public int aggregateTopics(String themeId) {
    // 只看近 7 日条目与近期仍活跃的选题
    LocalDateTime cutoff = LocalDateTime.now().minus(7, ChronoUnit.DAYS);
    List<SourceItemEntity> items = itemMapper.selectList(new LambdaQueryWrapper<SourceItemEntity>()
        .eq(SourceItemEntity::getThemeId, themeId)
        .ge(SourceItemEntity::getCreatedAt, cutoff)
        .orderByDesc(SourceItemEntity::getPublishedAt)
        .orderByDesc(SourceItemEntity::getCreatedAt));
    List<TopicClusterEntity> clusters = new ArrayList<>(topicMapper.selectList(
        new LambdaQueryWrapper<TopicClusterEntity>()
            .eq(TopicClusterEntity::getThemeId, themeId)
            .ge(TopicClusterEntity::getLastSeenAt, cutoff)));

    // 标题相似则并入已有选题，否则新建聚类（跳过厂商官网/产品页；发布年龄与有无日期不硬拒）
    for (SourceItemEntity item : items) {
      if (SourceLifecycleStatus.from(item.getLifecycleStatus()).shouldSkipAggregate()) {
        continue;
      }
      if (MarketingPageFilter.shouldSkip(item.getUrl(), item.getTitle())) {
        continue;
      }
      if (ThemeTitleRelevance.judge(
          topics.themeRelevanceProfile(themeId, item.getChannelId()),
          item.getTitle(),
          item.getSummary()).hardDrop()) {
        continue;
      }
      LocalDateTime published = PublishTimeUtil.resolvePublishedAt(item.getPublishedAt(), item.getUrl());
      if (published != null && item.getPublishedAt() == null) {
        item.setPublishedAt(published);
        itemMapper.updateById(item);
      }
      TopicClusterEntity match = clusters.stream()
          .filter(cluster -> TopicMathUtil.similar(cluster.getTitle(), item.getTitle()))
          .findFirst()
          .orElse(null);
      LocalDateTime now = LocalDateTime.now();
      LocalDateTime seenAt = published != null ? published : item.getCreatedAt();
      if (match == null) {
        // 新簇：以本条目标题/摘要为种子
        match = new TopicClusterEntity();
        match.setId(IdsUtil.newId());
        match.setThemeId(themeId);
        match.setTitle(ChineseTextNormalizer.toSimplified(item.getTitle()));
        String summary = ChineseTextNormalizer.toSimplified(
            item.getSummary() == null ? "" : item.getSummary());
        match.setSummary(summary.length() > 1200 ? summary.substring(0, 1200) : summary);
        match.setStatus(HotNewsTopicStatuses.DISCOVERED);
        match.setHeatScore(0);
        match.setWritingScore(0);
        match.setConfidenceScore(0);
        match.setScoreReason("");
        match.setFirstSeenAt(seenAt == null ? now : seenAt);
        match.setLastSeenAt(seenAt == null ? now : seenAt);
        match.setCreatedAt(now);
        match.setUpdatedAt(now);
        topicMapper.insert(match);
        clusters.add(match);
      }
      // 未关联则建立 topic-source 证据链
      Long linked = topicSourceMapper.selectCount(new LambdaQueryWrapper<TopicSourceEntity>()
          .eq(TopicSourceEntity::getTopicId, match.getId())
          .eq(TopicSourceEntity::getSourceItemId, item.getId()));
      if (linked == null || linked == 0) {
        TopicSourceEntity link = new TopicSourceEntity();
        link.setId(IdsUtil.newId());
        link.setTopicId(match.getId());
        link.setSourceItemId(item.getId());
        link.setRole(ROLE_EVIDENCE);
        link.setCreatedAt(now);
        topicSourceMapper.insert(link);
      }
      // 刷新 lastSeenAt：有发布时间则用之，否则用入库时间保持簇活跃
      LocalDateTime bumpAt = published != null ? published : item.getCreatedAt();
      if (bumpAt != null
          && (match.getLastSeenAt() == null || bumpAt.isAfter(match.getLastSeenAt()))) {
        match.setLastSeenAt(bumpAt);
      }
      match.setUpdatedAt(now);
      topicMapper.updateById(match);
    }

    // 重算热度/可写性/置信分；官网/产品页/推销选题直接过期
    LocalDateTime now = LocalDateTime.now();
    for (TopicClusterEntity cluster : clusters) {
      if (MarketingPageFilter.looksLikeBrandHomeTitle(cluster.getTitle())
          || MarketingPageFilter.looksLikeNewsPortalTitle(cluster.getTitle())
          || MarketingPageFilter.looksLikeCommerceProductTitle(cluster.getTitle())) {
        cluster.setStatus(HotNewsTopicStatuses.EXPIRED);
        cluster.setWritingScore(0);
        cluster.setHeatScore(0);
        cluster.setScoreReason("已过滤：疑似官网/商品推销或资讯栏目聚合页，不作为热点选题");
        cluster.setUpdatedAt(now);
        topicMapper.updateById(cluster);
        continue;
      }
      if (ThemeTitleRelevance.judge(
          topics.themeRelevanceProfile(themeId, null),
          cluster.getTitle(),
          cluster.getSummary()).hardDrop()) {
        cluster.setStatus(HotNewsTopicStatuses.EXPIRED);
        cluster.setWritingScore(0);
        cluster.setHeatScore(0);
        cluster.setScoreReason("已过滤：标题与当前主题搜索信号无明显相关");
        cluster.setUpdatedAt(now);
        topicMapper.updateById(cluster);
        continue;
      }
      List<TopicSourceEntity> links = topicSourceMapper.selectList(new LambdaQueryWrapper<TopicSourceEntity>()
          .eq(TopicSourceEntity::getTopicId, cluster.getId()));
      List<TopicMathUtil.SourceEvidence> evidence = new ArrayList<>();
      int marketingEvidence = 0;
      for (TopicSourceEntity link : links) {
        SourceItemEntity item = itemMapper.selectById(link.getSourceItemId());
        if (item == null) {
          continue;
        }
        if (SourceLifecycleStatus.from(item.getLifecycleStatus()).shouldSkipAggregate()) {
          continue;
        }
        if (MarketingPageFilter.shouldSkip(item.getUrl(), item.getTitle())) {
          marketingEvidence++;
          continue;
        }
        if (ThemeTitleRelevance.judge(
            topics.themeRelevanceProfile(themeId, item.getChannelId()),
            item.getTitle(),
            item.getSummary()).hardDrop()) {
          continue;
        }
        // 无日期或较旧仍计入证据，由 freshness 降权
        LocalDateTime seen = PublishTimeUtil.resolvePublishedAt(item.getPublishedAt(), item.getUrl());
        evidence.add(new TopicMathUtil.SourceEvidence(item.getUrl(), item.getChannelId(), seen));
      }
      if (evidence.isEmpty()) {
        cluster.setStatus(HotNewsTopicStatuses.EXPIRED);
        cluster.setWritingScore(0);
        cluster.setHeatScore(0);
        cluster.setScoreReason(marketingEvidence > 0
            ? "已过滤：来源均为厂商官网或产品落地页"
            : "已过滤：无可用证据来源");
        cluster.setUpdatedAt(now);
        topicMapper.updateById(cluster);
        continue;
      }
      // 簇摘要为空时，用成员信源事实速览拼成可读速览
      fillClusterSummaryFromMemberBriefs(cluster, links);
      TopicMathUtil.ClusterScore scored = TopicMathUtil.scoreCluster(
          cluster.getTitle(), cluster.getSummary(), evidence, now);
      cluster.setHeatScore(scored.heatScore());
      cluster.setWritingScore(scored.writingScore());
      cluster.setConfidenceScore(scored.confidenceScore());
      cluster.setVelocityScore(scored.velocityScore());
      cluster.setFreshnessScore(scored.freshnessScore());
      cluster.setEvidenceScore(scored.evidenceScore());
      cluster.setLifecycle(scored.lifecycle());
      cluster.setScoreReason(scored.scoreReason());
      // 写作价值由主题量表 AI 评分后再升 candidate；此处仅写入采集事实分
      cluster.setUpdatedAt(now);
      topicMapper.updateById(cluster);
      scoreDetailsStore.write(cluster.getId(), scored.scoreDetails());
    }
    // 按日限额把超额选题标 expired
    applyDailyTopicLimit(themeId);
    return clusters.size();
  }

  /**
   * 按自然日（东八区）执行每日雷达限额：
   * <ul>
   *   <li>当天：按写作价值保留前 {@link TopicMathUtil#DAILY_TOPIC_LIMIT} 条；超额且未确认的物理删除</li>
   *   <li>历史日：未确认选题一律删除（素材只在当天生效）；已确认保留</li>
   * </ul>
   *
   * @param themeId 主题 ID
   */
  public void applyDailyTopicLimit(String themeId) {
    List<TopicClusterEntity> rows = topicMapper.selectList(
        new LambdaQueryWrapper<TopicClusterEntity>().eq(TopicClusterEntity::getThemeId, themeId));
    if (rows.isEmpty()) {
      return;
    }
    ZoneId beijing = ZoneId.of("Asia/Shanghai");
    DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    String today = LocalDate.now(beijing).format(dayFmt);
    Map<String, List<TopicClusterEntity>> byDay = new LinkedHashMap<>();
    for (TopicClusterEntity row : rows) {
      LocalDateTime created = row.getCreatedAt() == null ? LocalDateTime.now() : row.getCreatedAt();
      byDay.computeIfAbsent(dayFmt.format(created), ignored -> new ArrayList<>()).add(row);
    }
    for (Map.Entry<String, List<TopicClusterEntity>> entry : byDay.entrySet()) {
      String day = entry.getKey();
      List<TopicClusterEntity> daily = entry.getValue();
      daily.sort((left, right) -> {
        int byScore = Integer.compare(
            right.getWritingScore() == null ? 0 : right.getWritingScore(),
            left.getWritingScore() == null ? 0 : left.getWritingScore());
        if (byScore != 0) {
          return byScore;
        }
        LocalDateTime leftSeen = left.getLastSeenAt() == null ? left.getCreatedAt() : left.getLastSeenAt();
        LocalDateTime rightSeen = right.getLastSeenAt() == null ? right.getCreatedAt() : right.getLastSeenAt();
        if (leftSeen == null || rightSeen == null) {
          return 0;
        }
        return rightSeen.compareTo(leftSeen);
      });
      if (today.equals(day)) {
        // 按写作分保留日限额内选题（与列表默认排序一致，不因来源形态优待）
        Set<String> topIds = new LinkedHashSet<>();
        for (TopicClusterEntity row : daily) {
          if (topIds.size() >= TopicMathUtil.DAILY_TOPIC_LIMIT) {
            break;
          }
          topIds.add(row.getId());
        }
        for (TopicClusterEntity row : daily) {
          if (topIds.contains(row.getId())) {
            if (HotNewsTopicStatuses.EXPIRED.equals(row.getStatus())) {
              row.setStatus(row.getWritingScore() != null
                      && row.getWritingScore() >= CANDIDATE_WRITING_SCORE_FLOOR
                  ? HotNewsTopicStatuses.CANDIDATE
                  : HotNewsTopicStatuses.DISCOVERED);
              topicMapper.updateById(row);
            }
            continue;
          }
          if (HotNewsTopicStatuses.isConfirmedLifecycle(row.getStatus())) {
            continue;
          }
          topicMapper.deleteById(row.getId());
        }
      } else {
        for (TopicClusterEntity row : daily) {
          if (HotNewsTopicStatuses.isConfirmedLifecycle(row.getStatus())) {
            continue;
          }
          topicMapper.deleteById(row.getId());
        }
      }
    }
  }

  /**
   * 为缺少速度/新鲜度落库的选题补算评分（迁移后旧数据）。
   *
   * @param themeId 主题 ID
   */
  public void refreshTopicScores(String themeId) {
    List<TopicClusterEntity> rows = topicMapper.selectList(
        new LambdaQueryWrapper<TopicClusterEntity>()
            .eq(TopicClusterEntity::getThemeId, themeId)
            .ne(TopicClusterEntity::getStatus, HotNewsTopicStatuses.EXPIRED));
    LocalDateTime now = LocalDateTime.now();
    for (TopicClusterEntity cluster : rows) {
      TopicScoreDetails previousDetails = scoreDetailsStore.read(cluster.getId());
      boolean missingDetails = previousDetails.getSourceCount() == 0
          && CollUtil.isEmpty(previousDetails.getSourceTypes())
          && CollUtil.isEmpty(previousDetails.getSignals())
          && StrUtil.isBlank(previousDetails.getOriginKind())
          && CollUtil.isEmpty(previousDetails.getDimensions());
      boolean missingScores = cluster.getVelocityScore() == null
          || cluster.getFreshnessScore() == null
          || ((cluster.getVelocityScore() == 0)
              && (cluster.getFreshnessScore() == 0)
              && missingDetails);
      if (!missingScores && !missingDetails) {
        continue;
      }
      List<TopicSourceEntity> links = topicSourceMapper.selectList(new LambdaQueryWrapper<TopicSourceEntity>()
          .eq(TopicSourceEntity::getTopicId, cluster.getId()));
      List<TopicMathUtil.SourceEvidence> evidence = new ArrayList<>();
      for (TopicSourceEntity link : links) {
        SourceItemEntity item = itemMapper.selectById(link.getSourceItemId());
        if (item == null) {
          continue;
        }
        LocalDateTime seen = item.getPublishedAt() != null ? item.getPublishedAt() : item.getCreatedAt();
        evidence.add(new TopicMathUtil.SourceEvidence(item.getUrl(), item.getChannelId(), seen));
      }
      fillClusterSummaryFromMemberBriefs(cluster, links);
      TopicMathUtil.ClusterScore scored = TopicMathUtil.scoreCluster(
          cluster.getTitle(), cluster.getSummary(), evidence, now);
      Integer previousWriting = cluster.getWritingScore();
      TopicScoreDetails mergedDetails = scored.scoreDetails();
      mergedDetails.setDimensions(previousDetails.getDimensions());
      mergedDetails.setRubricVersion(previousDetails.getRubricVersion());
      mergedDetails.setRubricSnapshot(previousDetails.getRubricSnapshot());
      mergedDetails.setRecommendUse(previousDetails.getRecommendUse());
      mergedDetails.setRiskNote(previousDetails.getRiskNote());
      if (StrUtil.isNotBlank(previousDetails.getOriginKind())) {
        mergedDetails.setOriginKind(previousDetails.getOriginKind());
      }
      cluster.setHeatScore(scored.heatScore());
      // 补算事实分时保留已有 AI 写作分
      if (previousWriting != null && previousWriting > 0) {
        cluster.setWritingScore(previousWriting);
      } else {
        cluster.setWritingScore(scored.writingScore());
      }
      cluster.setConfidenceScore(scored.confidenceScore());
      cluster.setVelocityScore(scored.velocityScore());
      cluster.setFreshnessScore(scored.freshnessScore());
      cluster.setEvidenceScore(scored.evidenceScore());
      cluster.setLifecycle(scored.lifecycle());
      if (previousWriting == null || previousWriting <= 0) {
        cluster.setScoreReason(scored.scoreReason());
      }
      cluster.setUpdatedAt(now);
      topicMapper.updateById(cluster);
      scoreDetailsStore.write(cluster.getId(), mergedDetails);
    }
  }

  /**
   * 当选题簇摘要为空或仅等于标题时，用成员信源事实速览拼成可读速览。
   *
   * @param cluster 选题簇
   * @param links   来源关联
   */
  public void fillClusterSummaryFromMemberBriefs(
      TopicClusterEntity cluster, List<TopicSourceEntity> links) {
    if (cluster == null || links == null || links.isEmpty()) {
      return;
    }
    String current = StrUtil.trim(StrUtil.nullToEmpty(cluster.getSummary()));
    String title = StrUtil.trim(StrUtil.nullToEmpty(cluster.getTitle()));
    boolean weak = StrUtil.isBlank(current)
        || current.equals(title)
        || current.length() < 24;
    if (!weak) {
      return;
    }
    List<String> briefs = new ArrayList<>();
    for (TopicSourceEntity link : links) {
      if (link == null || StrUtil.isBlank(link.getSourceItemId())) {
        continue;
      }
      SourceItemEntity item = itemMapper.selectById(link.getSourceItemId());
      if (item == null || SourceLifecycleStatus.from(item.getLifecycleStatus()).shouldSkipAggregate()) {
        continue;
      }
      String brief = StrUtil.trim(StrUtil.nullToEmpty(item.getSummary()));
      if (StrUtil.isBlank(brief) || brief.equals(StrUtil.trim(StrUtil.nullToEmpty(item.getTitle())))) {
        continue;
      }
      if (briefs.contains(brief)) {
        continue;
      }
      briefs.add(brief);
      if (briefs.size() >= 5) {
        break;
      }
    }
    if (briefs.isEmpty()) {
      return;
    }
    String merged = ChineseTextNormalizer.toSimplified(String.join("；", briefs));
    if (merged.length() > 1200) {
      merged = merged.substring(0, 1200);
    }
    cluster.setSummary(merged);
    cluster.setTranslatedSummary(merged);
  }
}
