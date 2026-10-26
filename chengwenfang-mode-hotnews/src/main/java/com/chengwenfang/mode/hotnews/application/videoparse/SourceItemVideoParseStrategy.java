package com.chengwenfang.mode.hotnews.application.videoparse;

import com.chengwenfang.sourcing.application.videoparse.SourceVideoParseItemOutcome;

import com.chengwenfang.sourcing.application.videoparse.VideoParseOwnerStrategy;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.common.ThemeTitleRelevance;
import com.chengwenfang.crawler.facade.MarketingPageFilter;
import com.chengwenfang.crawler.facade.PublishTimeUtil;
import com.chengwenfang.mode.hotnews.application.HotNewsCodec;
import com.chengwenfang.mode.hotnews.application.HotNewsTopicService;
import com.chengwenfang.mode.hotnews.application.SourceVideoContentAssistService;
import com.chengwenfang.sourcing.application.VideoParseErrorMessages;
import com.chengwenfang.mode.hotnews.application.VideoParseRunService;
import com.chengwenfang.mode.hotnews.application.VideoTranscriptService;
import com.chengwenfang.mode.hotnews.domain.ChineseTextNormalizer;
import com.chengwenfang.mode.hotnews.domain.TopicMathUtil;
import com.chengwenfang.sourcing.domain.entity.SourceItemEntity;
import com.chengwenfang.mode.hotnews.domain.entity.TopicClusterEntity;
import com.chengwenfang.mode.hotnews.domain.entity.TopicSourceEntity;
import com.chengwenfang.sourcing.facade.constant.VideoParseOwnerTypes;
import com.chengwenfang.sourcing.facade.enums.SourceContentIntent;
import com.chengwenfang.sourcing.facade.enums.SourceLifecycleStatus;
import com.chengwenfang.sourcing.facade.enums.SourceMediaType;
import com.chengwenfang.sourcing.facade.enums.VideoParseStatus;
import com.chengwenfang.sourcing.facade.enums.VideoParseStep;
import com.chengwenfang.sourcing.infrastructure.mapper.SourceItemMapper;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.TopicClusterMapper;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.TopicSourceMapper;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 雷达信源视频解析策略：待解析加载、单条解析后闸门、弃用与选题挂接。
 */
@Slf4j
@Component
public class SourceItemVideoParseStrategy implements VideoParseOwnerStrategy<SourceItemEntity> {

  /** 信源 Mapper。 */
  private final SourceItemMapper itemMapper;
  /** 选题簇 Mapper。 */
  private final TopicClusterMapper topicMapper;
  /** 选题-信源关联 Mapper。 */
  private final TopicSourceMapper topicSourceMapper;
  /** 字幕解析。 */
  private final VideoTranscriptService videoTranscriptService;
  /** 事实速览与意图判定。 */
  private final SourceVideoContentAssistService sourceVideoContentAssist;
  /** 解析状态共享表。 */
  private final VideoParseRunService videoParseRuns;
  /** 选题协作（主题相关性、簇摘要）。 */
  private final HotNewsTopicService topics;

  /**
   * @param itemMapper               信源 Mapper
   * @param topicMapper              选题簇 Mapper
   * @param topicSourceMapper        选题-信源关联 Mapper
   * @param videoTranscriptService   字幕解析
   * @param sourceVideoContentAssist 事实速览与意图
   * @param videoParseRuns           解析状态共享表
   * @param topics                   选题协作
   */
  public SourceItemVideoParseStrategy(
      SourceItemMapper itemMapper,
      TopicClusterMapper topicMapper,
      TopicSourceMapper topicSourceMapper,
      VideoTranscriptService videoTranscriptService,
      SourceVideoContentAssistService sourceVideoContentAssist,
      VideoParseRunService videoParseRuns,
      @Lazy HotNewsTopicService topics) {
    this.itemMapper = itemMapper;
    this.topicMapper = topicMapper;
    this.topicSourceMapper = topicSourceMapper;
    this.videoTranscriptService = videoTranscriptService;
    this.sourceVideoContentAssist = sourceVideoContentAssist;
    this.videoParseRuns = videoParseRuns;
    this.topics = topics;
  }

  @Override
  public String ownerType() {
    return VideoParseOwnerTypes.SOURCE_ITEM;
  }

  @Override
  public boolean supports(String ownerType) {
    return ownerType().equals(ownerType);
  }

  /**
   * 统计主题下待解析视频条数。
   *
   * @param themeId 主题 ID
   * @return 条数；无则 0
   */
  public long countPending(String themeId) {
    if (StrUtil.isBlank(themeId)) {
      return 0L;
    }
    Long pending = itemMapper.selectCount(new LambdaQueryWrapper<SourceItemEntity>()
        .eq(SourceItemEntity::getThemeId, themeId)
        .eq(SourceItemEntity::getMediaType, SourceMediaType.VIDEO.getValue())
        .eq(SourceItemEntity::getLifecycleStatus, SourceLifecycleStatus.PENDING_PARSE.getValue()));
    return pending == null ? 0L : pending;
  }

  /**
   * 加载待解析视频（限额、按创建时间倒序）。
   *
   * @param themeId 主题 ID
   * @param max     上限
   * @return 待处理列表
   */
  public List<SourceItemEntity> loadPendingTargets(String themeId, int max) {
    return itemMapper.selectList(new LambdaQueryWrapper<SourceItemEntity>()
        .eq(SourceItemEntity::getThemeId, themeId)
        .eq(SourceItemEntity::getMediaType, SourceMediaType.VIDEO.getValue())
        .eq(SourceItemEntity::getLifecycleStatus, SourceLifecycleStatus.PENDING_PARSE.getValue())
        .orderByDesc(SourceItemEntity::getCreatedAt)
        .last("LIMIT " + Math.max(1, max)));
  }

  /**
   * 加载主题下全部 pending_parse 信源（含非视频，供未启用时整批弃用）。
   *
   * @param themeId 主题 ID
   * @return 列表
   */
  public List<SourceItemEntity> loadAllPendingParse(String themeId) {
    return itemMapper.selectList(new LambdaQueryWrapper<SourceItemEntity>()
        .eq(SourceItemEntity::getThemeId, themeId)
        .eq(SourceItemEntity::getLifecycleStatus, SourceLifecycleStatus.PENDING_PARSE.getValue()));
  }

  /**
   * 单条：解析 → 事实速览/意图闸门 → ready 挂接或弃用。
   *
   * @param themeId 主题 ID
   * @param item    待解析信源
   * @return 处理结果
   */
  public SourceVideoParseItemOutcome processOne(String themeId, SourceItemEntity item) {
    SourceItemEntity fresh = itemMapper.selectById(item.getId());
    if (fresh == null) {
      return SourceVideoParseItemOutcome.SKIPPED;
    }
    videoTranscriptService.parseSourceItem(fresh, VideoParseStep.FETCH_SUBTITLE, null);
    fresh = itemMapper.selectById(item.getId());
    videoParseRuns.applyToSourceItem(fresh);
    if (fresh == null || !VideoParseStatus.from(fresh.getVideoParseStatus()).isSucceeded()) {
      discard(
          fresh == null ? item : fresh,
          StrUtil.blankToDefault(
              fresh == null ? null : fresh.getVideoParseError(), "视频解析失败"),
          null);
      return SourceVideoParseItemOutcome.DISCARDED;
    }
    // 事实速览覆盖 summary，再判定营销
    String factBrief = sourceVideoContentAssist.buildFactBrief(
        fresh.getTitle(), fresh.getVideoRawText(), fresh.getSummary());
    fresh.setSummary(factBrief);
    SourceContentIntent intent = sourceVideoContentAssist.judgeIntent(
        fresh.getTitle(), factBrief, fresh.getVideoRawText());
    fresh.setContentIntent(intent.getValue());
    if (intent.isMarketing()) {
      discard(fresh, "营销/推销向素材", SourceContentIntent.MARKETING);
      return SourceVideoParseItemOutcome.DISCARDED;
    }
    ThemeTitleRelevance.Decision themeDecision = ThemeTitleRelevance.judge(
        topics.themeRelevanceProfile(themeId, fresh.getChannelId()),
        fresh.getTitle(),
        factBrief);
    if (themeDecision.hardDrop()) {
      discard(fresh, "主题不相关：" + themeDecision.reason(), null);
      return SourceVideoParseItemOutcome.DISCARDED;
    }
    fresh.setLifecycleStatus(SourceLifecycleStatus.READY.getValue());
    fresh.setDiscardReason(null);
    fresh.setContentHash(HotNewsCodec.sha256(StrUtil.nullToEmpty(fresh.getSummary())));
    itemMapper.updateById(fresh);
    attachReady(themeId, fresh);
    return SourceVideoParseItemOutcome.READY;
  }

  /**
   * 将信源标为弃用。
   *
   * @param item   条目
   * @param reason 原因
   * @param intent 内容意图；可空
   */
  public void discard(SourceItemEntity item, String reason, SourceContentIntent intent) {
    if (item == null || StrUtil.isBlank(item.getId())) {
      return;
    }
    item.setLifecycleStatus(SourceLifecycleStatus.DISCARDED.getValue());
    item.setDiscardReason(StrUtil.maxLength(StrUtil.blankToDefault(reason, "已弃用"), 500));
    if (intent != null) {
      item.setContentIntent(intent.getValue());
    }
    itemMapper.updateById(item);
  }

  /**
   * 按主键重载并水合解析状态；不存在返回 null。
   *
   * @param ownerId 信源 ID
   * @return 实体；不存在时 null
   */
  @Override
  public SourceItemEntity reload(String ownerId) {
    if (StrUtil.isBlank(ownerId)) {
      return null;
    }
    SourceItemEntity item = itemMapper.selectById(ownerId);
    videoParseRuns.applyToSourceItem(item);
    return item;
  }

  /**
   * 信源解析状态与口播原文补齐到内存字段。
   *
   * @param item 信源实体
   */
  public void hydrate(SourceItemEntity item) {
    videoParseRuns.applyToSourceItem(item);
  }

  @Override
  public void markFailed(String ownerId, String errorMessage) {
    if (StrUtil.isBlank(ownerId)) {
      return;
    }
    try {
      SourceItemEntity item = itemMapper.selectById(ownerId);
      if (item == null) {
        return;
      }
      videoParseRuns.applyToSourceItem(item);
      VideoParseStatus status = VideoParseStatus.from(item.getVideoParseStatus());
      if (status.isSucceeded()) {
        return;
      }
      VideoParseStep step = VideoParseStep.from(item.getVideoParseStep());
      item.setVideoParseStatus(VideoParseStatus.FAILED.getValue());
      item.setVideoParseError(StrUtil.maxLength(
          VideoParseErrorMessages.humanize(errorMessage, step), 500));
      videoParseRuns.upsertFromSourceItem(item);
    } catch (Exception ignored) {
      log.warn("标记信源视频解析失败状态时出错 itemId={}", ownerId);
    }
  }

  /**
   * 将已就绪信源增量挂接到选题簇（标题相似则并入，否则新建）。
   *
   * @param themeId 主题 ID
   * @param item    就绪条目
   */
  private void attachReady(String themeId, SourceItemEntity item) {
    if (item == null || !SourceLifecycleStatus.from(item.getLifecycleStatus()).isReady()) {
      return;
    }
    if (MarketingPageFilter.shouldSkip(item.getUrl(), item.getTitle())) {
      discard(item, "标题命中推销过滤", SourceContentIntent.MARKETING);
      return;
    }
    ThemeTitleRelevance.Decision themeDecision = ThemeTitleRelevance.judge(
        topics.themeRelevanceProfile(themeId, item.getChannelId()),
        item.getTitle(),
        item.getSummary());
    if (themeDecision.hardDrop()) {
      discard(item, "主题不相关：" + themeDecision.reason(), null);
      return;
    }
    // 近 7 日活跃簇作合并目标；发布年龄不再硬拒
    LocalDateTime cutoff = LocalDateTime.now().minus(7, ChronoUnit.DAYS);
    LocalDateTime published = PublishTimeUtil.resolvePublishedAt(item.getPublishedAt(), item.getUrl());
    List<TopicClusterEntity> clusters = topicMapper.selectList(
        new LambdaQueryWrapper<TopicClusterEntity>()
            .eq(TopicClusterEntity::getThemeId, themeId)
            .ge(TopicClusterEntity::getLastSeenAt, cutoff));
    TopicClusterEntity match = clusters.stream()
        .filter(cluster -> TopicMathUtil.similar(cluster.getTitle(), item.getTitle()))
        .findFirst()
        .orElse(null);
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime seenAt = published != null ? published : item.getCreatedAt();
    if (match == null) {
      match = new TopicClusterEntity();
      match.setId(IdsUtil.newId());
      match.setThemeId(themeId);
      match.setTitle(ChineseTextNormalizer.toSimplified(item.getTitle()));
      String summary = ChineseTextNormalizer.toSimplified(
          item.getSummary() == null ? "" : item.getSummary());
      match.setSummary(summary.length() > 1200 ? summary.substring(0, 1200) : summary);
      match.setStatus("discovered");
      match.setHeatScore(0);
      match.setWritingScore(0);
      match.setConfidenceScore(0);
      match.setScoreReason("");
      match.setFirstSeenAt(seenAt == null ? now : seenAt);
      match.setLastSeenAt(seenAt == null ? now : seenAt);
      match.setCreatedAt(now);
      match.setUpdatedAt(now);
      topicMapper.insert(match);
    }
    Long linked = topicSourceMapper.selectCount(new LambdaQueryWrapper<TopicSourceEntity>()
        .eq(TopicSourceEntity::getTopicId, match.getId())
        .eq(TopicSourceEntity::getSourceItemId, item.getId()));
    if (linked == null || linked == 0) {
      TopicSourceEntity link = new TopicSourceEntity();
      link.setId(IdsUtil.newId());
      link.setTopicId(match.getId());
      link.setSourceItemId(item.getId());
      link.setRole("evidence");
      link.setCreatedAt(now);
      topicSourceMapper.insert(link);
    }
    if (published != null
        && (match.getLastSeenAt() == null || published.isAfter(match.getLastSeenAt()))) {
      match.setLastSeenAt(published);
    }
    List<TopicSourceEntity> attachedLinks = topicSourceMapper.selectList(
        new LambdaQueryWrapper<TopicSourceEntity>()
            .eq(TopicSourceEntity::getTopicId, match.getId()));
    topics.fillClusterSummaryFromMemberBriefs(match, attachedLinks);
    match.setUpdatedAt(now);
    topicMapper.updateById(match);
  }
}
