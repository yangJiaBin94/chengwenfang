package com.chengwenfang.mode.hotnews.application;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.job.facade.JobFacade;
import com.chengwenfang.job.facade.dto.view.JobView;
import com.chengwenfang.mode.hotnews.domain.ChineseTextNormalizer;
import com.chengwenfang.mode.hotnews.domain.ResearchContentClassifier;
import com.chengwenfang.mode.hotnews.domain.entity.RadarBatchEntity;
import com.chengwenfang.mode.hotnews.domain.entity.TopicClusterEntity;
import com.chengwenfang.mode.hotnews.domain.entity.TopicSourceEntity;
import com.chengwenfang.mode.hotnews.facade.HotNewsFacade;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsTopicStatuses;
import com.chengwenfang.mode.hotnews.facade.constant.RadarBatchStatuses;
import com.chengwenfang.mode.hotnews.facade.dto.request.RadarSurvivorPersistRequest;
import com.chengwenfang.mode.hotnews.facade.dto.view.RadarPersistClusterResult;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.RadarBatchMapper;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.TopicClusterMapper;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.TopicSourceMapper;
import com.chengwenfang.sourcing.domain.entity.SourceFetchRunEntity;
import com.chengwenfang.sourcing.domain.entity.SourceItemEntity;
import com.chengwenfang.sourcing.facade.constant.VideoParseOwnerTypes;
import com.chengwenfang.sourcing.facade.enums.SourceContentIntent;
import com.chengwenfang.sourcing.facade.enums.SourceLifecycleStatus;
import com.chengwenfang.sourcing.facade.enums.SourceMediaType;
import com.chengwenfang.sourcing.infrastructure.mapper.SourceFetchRunMapper;
import com.chengwenfang.sourcing.infrastructure.mapper.SourceItemMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/**
 * 雷达批次：创建/完成、幸存信源落库成簇与采集运行列表。
 */
@Service
public class RadarBatchService {

  /** 雷达批次表。 */
  private final RadarBatchMapper radarBatchMapper;
  /** 信源条目表。 */
  private final SourceItemMapper itemMapper;
  /** 选题-信源关联表。 */
  private final TopicSourceMapper topicSourceMapper;
  /** 选题聚类表。 */
  private final TopicClusterMapper topicMapper;
  /** 抓取运行表（列雷达 Job）。 */
  private final SourceFetchRunMapper runMapper;
  /** 任务门面。 */
  private final JobFacade jobs;
  /** 热点门面（成簇后聚合）；@Lazy 破环。 */
  private final HotNewsFacade hotNews;
  /** 视频解析共享表。 */
  private final VideoParseRunService videoParseRuns;

  /**
   * @param radarBatchMapper  雷达批次表
   * @param itemMapper        信源条目表
   * @param topicSourceMapper 选题-信源关联
   * @param topicMapper       选题聚类表
   * @param runMapper         抓取运行表
   * @param jobs              任务门面
   * @param hotNews           热点门面（@Lazy）
   * @param videoParseRuns    视频解析共享表
   */
  public RadarBatchService(
      RadarBatchMapper radarBatchMapper,
      SourceItemMapper itemMapper,
      TopicSourceMapper topicSourceMapper,
      TopicClusterMapper topicMapper,
      SourceFetchRunMapper runMapper,
      JobFacade jobs,
      @Lazy HotNewsFacade hotNews,
      VideoParseRunService videoParseRuns) {
    this.radarBatchMapper = radarBatchMapper;
    this.itemMapper = itemMapper;
    this.topicSourceMapper = topicSourceMapper;
    this.topicMapper = topicMapper;
    this.runMapper = runMapper;
    this.jobs = jobs;
    this.hotNews = hotNews;
    this.videoParseRuns = videoParseRuns;
  }

  /**
   * 确保雷达批次行存在（幂等插入）。
   *
   * @param batchId 批次 ID
   * @param themeId 主题 ID
   * @param jobId   触发 Job ID，可空
   */
  @Transactional
  public void ensureRadarBatch(String batchId, String themeId, String jobId) {
    if (StrUtil.isBlank(batchId) || StrUtil.isBlank(themeId)) {
      return;
    }
    if (radarBatchMapper.selectById(batchId) != null) {
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    radarBatchMapper.insert(RadarBatchEntity.builder()
        .id(batchId)
        .themeId(themeId)
        .jobId(StrUtil.blankToDefault(jobId, null))
        .status(RadarBatchStatuses.RUNNING)
        .startedAt(now)
        .createdAt(now)
        .build());
  }

  /**
   * 将雷达批次标为已完成。
   *
   * @param batchId 批次 ID
   */
  @Transactional
  public void completeRadarBatch(String batchId) {
    finishRadarBatch(batchId, RadarBatchStatuses.COMPLETED);
  }

  /**
   * 将雷达批次标为失败。
   *
   * @param batchId 批次 ID
   */
  @Transactional
  public void failRadarBatch(String batchId) {
    finishRadarBatch(batchId, RadarBatchStatuses.FAILED);
  }

  /**
   * 更新批次终态与结束时间。
   *
   * @param batchId 批次 ID
   * @param status  终态
   */
  void finishRadarBatch(String batchId, String status) {
    if (StrUtil.isBlank(batchId)) {
      return;
    }
    RadarBatchEntity batch = radarBatchMapper.selectById(batchId);
    if (batch == null) {
      return;
    }
    batch.setStatus(status);
    batch.setFinishedAt(LocalDateTime.now());
    radarBatchMapper.updateById(batch);
  }

  /**
   * 落库雷达幸存者并触发选题聚合，回写批次 ID 与成员速览。
   *
   * @param themeId      主题 ID
   * @param radarBatchId 批次 ID
   * @param survivors    幸存者列表
   * @return 持久化与成簇结果
   */
  @Transactional
  public RadarPersistClusterResult persistAndClusterRadarSurvivors(
      String themeId, String radarBatchId, List<RadarSurvivorPersistRequest> survivors) {
    RadarPersistClusterResult result = RadarPersistClusterResult.builder().build();
    if (StrUtil.isBlank(themeId) || survivors == null || survivors.isEmpty()) {
      return result;
    }
    // 落库前确保批次行存在（历史会话可能尚未写过）
    ensureRadarBatch(radarBatchId, themeId, null);
    int inserted = 0;
    int deduped = 0;
    List<String> sourceIds = new ArrayList<>();
    Map<String, String> briefBySourceId = new LinkedHashMap<>();
    for (RadarSurvivorPersistRequest survivor : survivors) {
      if (survivor == null) {
        continue;
      }
      // 去重时仍回写事实速览并返回已有 id，保证成簇摘要能汇总成员概要
      RadarSurvivorUpsertOutcome outcome = upsertRadarSurvivor(themeId, survivor);
      if (outcome == null || StrUtil.isBlank(outcome.sourceId())) {
        continue;
      }
      if (outcome.created()) {
        inserted++;
      } else {
        deduped++;
      }
      sourceIds.add(outcome.sourceId());
      String brief = StrUtil.blankToDefault(
          survivor.getFactBrief(),
          outcome.storedSummary());
      briefBySourceId.put(outcome.sourceId(), StrUtil.nullToEmpty(brief));
      if (StrUtil.isNotBlank(survivor.getTempId())) {
        result.getSourceIdByTempId().put(survivor.getTempId(), outcome.sourceId());
      }
    }
    result.setInserted(inserted);
    result.setDeduped(deduped);
    // 复用门面选题聚合与评分
    hotNews.aggregateTopics(themeId);
    // 同批打标共用时间戳，避免 orderBy updated_at 因毫秒差打乱稳定序
    LocalDateTime batchTaggedAt = LocalDateTime.now();
    // 将本轮信源关联的簇打上批次，并收集成员速览（含去重命中的已有条目）
    for (String sourceId : sourceIds) {
      List<TopicSourceEntity> links = topicSourceMapper.selectList(new LambdaQueryWrapper<TopicSourceEntity>()
          .eq(TopicSourceEntity::getSourceItemId, sourceId));
      for (TopicSourceEntity link : links) {
        if (link == null || StrUtil.isBlank(link.getTopicId())) {
          continue;
        }
        TopicClusterEntity cluster = topicMapper.selectById(link.getTopicId());
        if (cluster == null) {
          continue;
        }
        cluster.setRadarBatchId(radarBatchId);
        cluster.setUpdatedAt(batchTaggedAt);
        topicMapper.updateById(cluster);
        if (!result.getTopicIds().contains(cluster.getId())) {
          result.getTopicIds().add(cluster.getId());
          result.getTitleByTopicId().put(cluster.getId(), cluster.getTitle());
          result.getMemberBriefsByTopicId().put(cluster.getId(), new ArrayList<>());
        }
        String brief = briefBySourceId.get(sourceId);
        if (StrUtil.isBlank(brief)) {
          SourceItemEntity item = itemMapper.selectById(sourceId);
          brief = item == null ? "" : StrUtil.nullToEmpty(item.getSummary());
        }
        if (StrUtil.isNotBlank(brief)
            && !result.getMemberBriefsByTopicId().get(cluster.getId()).contains(brief)) {
          result.getMemberBriefsByTopicId().get(cluster.getId()).add(brief);
        }
      }
    }
    result.setTopicCount(result.getTopicIds().size());
    return result;
  }

  /**
   * 列出本雷达批次下未过期选题实体（视图映射由门面完成）。
   *
   * @param themeId      主题 ID
   * @param radarBatchId 批次 ID
   * @return 选题实体列表
   */
  public List<TopicClusterEntity> listRadarBatchClusters(String themeId, String radarBatchId) {
    if (StrUtil.isBlank(themeId) || StrUtil.isBlank(radarBatchId)) {
      return List.of();
    }
    return topicMapper.selectList(new LambdaQueryWrapper<TopicClusterEntity>()
        .eq(TopicClusterEntity::getThemeId, themeId)
        .eq(TopicClusterEntity::getRadarBatchId, radarBatchId)
        .ne(TopicClusterEntity::getStatus, HotNewsTopicStatuses.EXPIRED)
        .orderByDesc(TopicClusterEntity::getUpdatedAt)
        .orderByAsc(TopicClusterEntity::getTitle)
        .orderByAsc(TopicClusterEntity::getId));
  }

  /**
   * 列出主题下近期抓取运行对应的 Job 视图。
   *
   * @param themeId 主题 ID
   * @return Job 列表（最多 100 条运行）
   */
  public List<JobView> listRadarCollectRuns(String themeId) {
    if (StrUtil.isBlank(themeId)) {
      return List.of();
    }
    List<SourceFetchRunEntity> runs = runMapper.selectList(new LambdaQueryWrapper<SourceFetchRunEntity>()
        .eq(SourceFetchRunEntity::getThemeId, themeId)
        .orderByDesc(SourceFetchRunEntity::getCreatedAt)
        .last("LIMIT 100"));
    List<JobView> out = new ArrayList<>();
    for (SourceFetchRunEntity run : runs) {
      if (run == null || StrUtil.isBlank(run.getJobId())) {
        continue;
      }
      try {
        JobView job = jobs.get(run.getJobId());
        if (job != null) {
          out.add(job);
        }
      } catch (Exception ignored) {
        // 跳过缺失 Job
      }
    }
    return out;
  }

  /**
   * 雷达幸存者落库结果。
   */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  static final class RadarSurvivorUpsertOutcome {
    /** 信源 ID。 */
    private final String sourceId;
    /** 是否新建。 */
    private final boolean created;
    /** 落库后的事实速览。 */
    private final String storedSummary;
  }

  /**
   * 插入或更新单条雷达幸存者；去重命中时回写更完整的事实速览并返回已有 id。
   *
   * @param themeId  主题 ID
   * @param survivor 幸存者
   * @return 落库结果；参数无效时 null
   */
  RadarSurvivorUpsertOutcome upsertRadarSurvivor(
      String themeId, RadarSurvivorPersistRequest survivor) {
    String externalId = StrUtil.firstNonBlank(survivor.getUrl(), survivor.getTitle());
    if (externalId == null || StrUtil.isBlank(survivor.getChannelId())) {
      return null;
    }
    if (externalId.length() > 500) {
      externalId = externalId.substring(0, 500);
    }
    String summary = ChineseTextNormalizer.toSimplified(StrUtil.nullToEmpty(survivor.getFactBrief()));
    if (summary.length() > 4000) {
      summary = summary.substring(0, 4000);
    }
    SourceItemEntity existing = itemMapper.selectOne(new LambdaQueryWrapper<SourceItemEntity>()
        .eq(SourceItemEntity::getChannelId, survivor.getChannelId())
        .eq(SourceItemEntity::getExternalId, externalId)
        .last("LIMIT 1"));
    if (existing != null) {
      // 去重命中：用本轮解析出的事实速览覆盖空/更短摘要
      boolean shouldWriteBrief = StrUtil.isNotBlank(summary)
          && (StrUtil.isBlank(existing.getSummary())
              || summary.length() > StrUtil.nullToEmpty(existing.getSummary()).length());
      if (shouldWriteBrief) {
        existing.setSummary(summary);
        existing.setContentHash(sha256(summary));
      }
      if (SourceLifecycleStatus.from(existing.getLifecycleStatus()).shouldSkipAggregate()
          && StrUtil.isNotBlank(summary)) {
        existing.setLifecycleStatus(SourceLifecycleStatus.READY.getValue());
        existing.setDiscardReason(null);
      }
      boolean video = ResearchContentClassifier.isVideo(existing.getUrl(), existing.getTitle());
      existing.setMediaType(video ? SourceMediaType.VIDEO.getValue() : SourceMediaType.ARTICLE.getValue());
      existing.setSiteLabel(ResearchContentClassifier.siteLabel(existing.getUrl()));
      itemMapper.updateById(existing);
      return new RadarSurvivorUpsertOutcome(
          existing.getId(),
          false,
          StrUtil.blankToDefault(existing.getSummary(), summary));
    }
    SourceItemEntity entity = new SourceItemEntity();
    entity.setId(IdsUtil.newId());
    entity.setThemeId(themeId);
    entity.setChannelId(survivor.getChannelId());
    entity.setExternalId(externalId);
    entity.setUrl(StrUtil.nullToEmpty(survivor.getUrl()));
    entity.setTitle(ChineseTextNormalizer.toSimplified(
        StrUtil.blankToDefault(survivor.getTitle(), "未命名来源")));
    entity.setAuthor(survivor.getAuthor());
    entity.setPublishedAt(survivor.getPublishedAt());
    entity.setSummary(summary);
    // 落库前再按 URL/标题识别，避免会话里过期的站点/形态标签写入
    boolean video = ResearchContentClassifier.isVideo(entity.getUrl(), entity.getTitle());
    entity.setMediaType(video ? SourceMediaType.VIDEO.getValue() : SourceMediaType.ARTICLE.getValue());
    entity.setSiteLabel(ResearchContentClassifier.siteLabel(entity.getUrl()));
    entity.setLifecycleStatus(SourceLifecycleStatus.READY.getValue());
    entity.setContentIntent(SourceContentIntent.KNOWLEDGE.getValue());
    entity.setVideoRawText(null);
    entity.setContentHash(sha256(entity.getSummary()));
    entity.setCreatedAt(LocalDateTime.now());
    itemMapper.insert(entity);
    if (video) {
      videoParseRuns.ensureNone(VideoParseOwnerTypes.SOURCE_ITEM, entity.getId(), entity.getUrl());
    }
    return new RadarSurvivorUpsertOutcome(entity.getId(), true, summary);
  }

  /**
   * 计算 UTF-8 文本的 SHA-256 十六进制摘要；算法异常时返回空串。
   *
   * @param value 原文
   * @return 小写十六进制 hash，或异常时的 ""
   */
  static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ignored) {
      return "";
    }
  }

}
