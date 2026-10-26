package com.chengwenfang.mode.hotnews.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.common.ThemeTitleRelevance;
import com.chengwenfang.crawler.facade.MarketingPageFilter;
import com.chengwenfang.crawler.facade.PublishTimeUtil;
import com.chengwenfang.crawler.facade.dto.view.FetchedItemView;
import com.chengwenfang.mode.hotnews.domain.ChineseTextNormalizer;
import com.chengwenfang.mode.hotnews.domain.ResearchContentClassifier;
import com.chengwenfang.sourcing.domain.entity.SourceChannelEntity;
import com.chengwenfang.sourcing.domain.entity.SourceFetchChannelRunEntity;
import com.chengwenfang.sourcing.domain.entity.SourceFetchRunEntity;
import com.chengwenfang.sourcing.domain.entity.SourceItemEntity;
import com.chengwenfang.mode.hotnews.domain.entity.TopicClusterEntity;
import com.chengwenfang.mode.hotnews.domain.entity.TopicResearchRunEntity;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsArticleStatuses;
import com.chengwenfang.sourcing.facade.constant.HotNewsFetchKeys;
import com.chengwenfang.sourcing.facade.constant.VideoParseOwnerTypes;
import com.chengwenfang.sourcing.facade.dto.view.HotNewsChannelRunView;
import com.chengwenfang.sourcing.facade.dto.view.HotNewsFetchRunView;
import com.chengwenfang.sourcing.facade.dto.view.HotNewsItemView;
import com.chengwenfang.sourcing.facade.dto.view.HotNewsPersistResult;
import com.chengwenfang.sourcing.facade.enums.ChannelRunStatus;
import com.chengwenfang.sourcing.facade.enums.SourceLifecycleStatus;
import com.chengwenfang.sourcing.facade.enums.SourceMediaType;
import com.chengwenfang.sourcing.infrastructure.mapper.SourceChannelMapper;
import com.chengwenfang.sourcing.infrastructure.mapper.SourceFetchChannelRunMapper;
import com.chengwenfang.sourcing.infrastructure.mapper.SourceFetchRunMapper;
import com.chengwenfang.sourcing.infrastructure.mapper.SourceItemMapper;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.TopicClusterMapper;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.TopicResearchRunMapper;
import com.chengwenfang.job.facade.JobFacade;
import com.chengwenfang.job.facade.constant.JobEntityTypes;
import com.chengwenfang.job.facade.constant.JobTypes;
import com.chengwenfang.job.facade.dto.job.HotNewsFetchJobInput;
import com.chengwenfang.job.facade.dto.job.JobPayloads;
import com.chengwenfang.job.facade.dto.view.JobView;
import com.chengwenfang.job.facade.enums.JobStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 热点抓取：运行入队、条目落库、运行收尾与活跃任务探测。
 * <p>由 {@link HotNewsFacadeImpl} 薄委托，不对外暴露。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotNewsFetchService {

  /** 抓取运行表。 */
  private final SourceFetchRunMapper runMapper;
  /** 频道级抓取运行表。 */
  private final SourceFetchChannelRunMapper channelRunMapper;
  /** 信源条目表。 */
  private final SourceItemMapper itemMapper;
  /** 信源频道表。 */
  private final SourceChannelMapper channelMapper;
  /** 选题表（活跃任务探测用）。 */
  private final TopicClusterMapper topicMapper;
  /** 调研运行表（活跃任务探测用）。 */
  private final TopicResearchRunMapper researchRunMapper;
  /** 热点作品存储（成文任务探测用）。 */
  private final HotNewsWorkStore workStore;
  /** 任务门面，入队 HOT_NEWS_FETCH。 */
  private final JobFacade jobs;
  /** 视频解析共享表。 */
  private final VideoParseRunService videoParseRuns;
  /** 选题协作服务，提供主题相关性画像。 */
  private final HotNewsTopicService topics;

  /**
   * 主题下是否仍有进行中的抓取 / 研究 / 成文任务。
   *
   * @param themeId 主题主键
   * @return 存在进行中任务时为 true
   */
  public boolean hasActiveJobsForTheme(String themeId) {
    if (StrUtil.isBlank(themeId)) {
      return false;
    }
    // 抓取运行仍在排队或执行
    Long fetchActive = runMapper.selectCount(new LambdaQueryWrapper<SourceFetchRunEntity>()
        .eq(SourceFetchRunEntity::getThemeId, themeId)
        .in(SourceFetchRunEntity::getStatus,
            JobStatus.PENDING.getValue(), JobStatus.RUNNING.getValue()));
    if (fetchActive != null && fetchActive > 0) {
      return true;
    }
    List<String> topicIds = topicMapper.selectList(new LambdaQueryWrapper<TopicClusterEntity>()
            .eq(TopicClusterEntity::getThemeId, themeId)
            .select(TopicClusterEntity::getId))
        .stream()
        .map(TopicClusterEntity::getId)
        .filter(StrUtil::isNotBlank)
        .toList();
    if (topicIds.isEmpty()) {
      return false;
    }
    // 选题研究运行未结束
    Long researchActive = researchRunMapper.selectCount(new LambdaQueryWrapper<TopicResearchRunEntity>()
        .in(TopicResearchRunEntity::getTopicId, topicIds)
        .in(TopicResearchRunEntity::getStatus,
            JobStatus.PENDING.getValue(), JobStatus.RUNNING.getValue()));
    if (researchActive != null && researchActive > 0) {
      return true;
    }
    // 成文任务进行中：查 hotnews_works.status
    return workStore.hasStatus(topicIds, HotNewsArticleStatuses.GENERATING);
  }

  /**
   * 启动主题抓取任务：写入 fetch_run 并入队 HOT_NEWS_FETCH。
   *
   * @param themeId    主题 ID，写入运行记录与任务载荷
   * @param channelIds 可选频道 ID 列表；null 视为空列表（由 Worker 再解析）
   * @param hours      回溯小时数，传入任务 input
   * @return 已创建的抓取任务视图
   */
  @Transactional
  public JobView startFetch(String themeId, List<String> channelIds, int hours) {
    return startFetch(themeId, channelIds, hours, null);
  }

  /**
   * 启动主题抓取任务（可附带额外 Job input）。
   *
   * @param themeId    主题 ID
   * @param channelIds 可选频道 ID 列表；null 视为空列表
   * @param hours      回溯小时数
   * @param jobInput   额外 Job 入参；可为 null
   * @return 已创建的抓取任务视图
   */
  @Transactional
  public JobView startFetch(
      String themeId, List<String> channelIds, int hours, Object jobInput) {
    // 先落运行壳，状态 pending，供任务与列表关联
    SourceFetchRunEntity run = new SourceFetchRunEntity();
    run.setId(IdsUtil.newId());
    run.setThemeId(themeId);
    run.setStatus(JobStatus.PENDING.getValue());
    run.setHours(hours);
    run.setChannelCount(channelIds == null ? 0 : channelIds.size());
    run.setItemCount(0);
    run.setCreatedAt(LocalDateTime.now());
    HotNewsFetchJobInput input = HotNewsFetchJobInput.from(JobPayloads.asMap(jobInput));
    input.put(HotNewsFetchKeys.THEME_ID, themeId);
    input.put(HotNewsFetchKeys.CHANNEL_IDS, channelIds == null ? List.of() : channelIds);
    input.put(HotNewsFetchKeys.HOURS, hours);
    input.put(HotNewsFetchKeys.FETCH_RUN_ID, run.getId());
    // 入队抓取任务，entityId 指向本 run
    JobView job = jobs.create(
        JobTypes.HOT_NEWS_FETCH,
        JobEntityTypes.SOURCE_FETCH_RUN,
        run.getId(),
        input);
    run.setJobId(job.getId());
    runMapper.insert(run);
    log.info("热点抓取已入队 jobId={} themeId={} channels={} hours={}",
        job.getId(), themeId, run.getChannelCount(), hours);
    return job;
  }

  /**
   * 列出抓取运行历史（最多 100 条），按创建时间倒序。
   *
   * @param themeId 主题 ID；为 null 时不过滤主题
   * @return 抓取运行视图列表
   */
  public List<HotNewsFetchRunView> listFetchRuns(String themeId) {
    return runMapper.selectList(new LambdaQueryWrapper<SourceFetchRunEntity>()
            .eq(themeId != null, SourceFetchRunEntity::getThemeId, themeId)
            .orderByDesc(SourceFetchRunEntity::getCreatedAt)
            .last("LIMIT 100"))
        .stream()
        .map(this::fetchRunView)
        .toList();
  }

  /**
   * 按主键读取抓取运行。
   *
   * @param runId 抓取运行 ID
   * @return 运行视图
   * @throws BusinessException 不存在时 404
   */
  public HotNewsFetchRunView getFetchRun(String runId) {
    SourceFetchRunEntity run = runMapper.selectById(runId);
    if (run == null) {
      throw new BusinessException(ErrorCodes.FETCH_RUN_NOT_FOUND, "抓取运行不存在: " + runId, 404);
    }
    return fetchRunView(run);
  }

  /**
   * 列出已抓取信源条目，按创建时间倒序；limit 钳制在 1..200。
   *
   * @param themeId 主题 ID；为 null 时不过滤主题
   * @param limit   期望条数，实际取 {@code max(1, min(limit, 200))}
   * @return 信源条目视图列表
   */
  public List<HotNewsItemView> listItems(String themeId, int limit) {
    // 限制上限避免一次拉太大
    return itemMapper.selectList(new LambdaQueryWrapper<SourceItemEntity>()
            .eq(themeId != null, SourceItemEntity::getThemeId, themeId)
            .orderByDesc(SourceItemEntity::getCreatedAt)
            .last("LIMIT " + Math.max(1, Math.min(limit, 200))))
        .stream()
        .map(this::itemView)
        .toList();
  }

  /**
   * 批量落库抓取结果，统计新增与去重条数。
   *
   * @param themeId   主题 ID
   * @param channelId 频道 ID
   * @param items     抓取条目列表
   * @return 插入/去重计数
   */
  public HotNewsPersistResult persistFetchedItems(
      String themeId, String channelId, List<FetchedItemView> items) {
    int inserted = 0;
    int deduped = 0;
    for (FetchedItemView item : items) {
      // saveItem 返回 false 表示已存在或无效
      if (saveItem(themeId, channelId, item)) {
        inserted++;
      } else {
        deduped++;
      }
    }
    return HotNewsPersistResult.builder().inserted(inserted).deduped(deduped).build();
  }

  /**
   * 保存单条抓取结果；以 url 或 title 作 externalId，频道内去重。
   *
   * @param themeId   主题 ID
   * @param channelId 频道 ID
   * @param item      单条抓取结果
   * @return true 表示新插入；false 表示无有效 ID 或已存在（计为去重）
   */
  private boolean saveItem(String themeId, String channelId, FetchedItemView item) {
    if (MarketingPageFilter.shouldSkip(item.getUrl(), item.getTitle())) {
      return false;
    }
    if (ThemeTitleRelevance.shouldSkip(topics.themeRelevanceProfile(themeId, channelId), item.getTitle())) {
      log.info("主题不相关跳过入库 title={} themeId={}", item.getTitle(), themeId);
      return false;
    }
    // 优先 URL，其次标题，作为频道内外部唯一键
    String externalId = StrUtil.firstNonBlank(item.getUrl(), item.getTitle());
    if (externalId == null) {
      return false;
    }
    // 截断过长 ID，避免超列宽
    if (externalId.length() > 500) {
      externalId = externalId.substring(0, 500);
    }
    Long existing = itemMapper.selectCount(new LambdaQueryWrapper<SourceItemEntity>()
        .eq(SourceItemEntity::getChannelId, channelId)
        .eq(SourceItemEntity::getExternalId, externalId));
    if (existing != null && existing > 0) {
      return false;
    }
    String summary = item.getSummary() == null ? "" : item.getSummary();
    SourceItemEntity entity = new SourceItemEntity();
    entity.setId(IdsUtil.newId());
    entity.setThemeId(themeId);
    entity.setChannelId(channelId);
    entity.setExternalId(externalId);
    entity.setUrl(item.getUrl() == null ? "" : item.getUrl());
    String title = StrUtil.isBlank(item.getTitle())
        ? "未命名来源"
        : ChineseTextNormalizer.toSimplified(item.getTitle());
    entity.setTitle(title);
    entity.setAuthor(item.getAuthor());
    LocalDateTime published = PublishTimeUtil.resolvePublishedAt(item.getPublishedAt(), item.getUrl());
    entity.setPublishedAt(published);
    // 摘要截断到 4000，并记 hash 供后续变更检测；繁体统一为简体
    String normalizedSummary = ChineseTextNormalizer.toSimplified(summary);
    entity.setSummary(normalizedSummary.length() > 4000
        ? normalizedSummary.substring(0, 4000)
        : normalizedSummary);
    // 打上来源站与文章/视频类型；视频待解析，文章直接就绪
    boolean video = ResearchContentClassifier.isVideo(entity.getUrl(), entity.getTitle());
    entity.setMediaType(video ? SourceMediaType.VIDEO.getValue() : SourceMediaType.ARTICLE.getValue());
    entity.setSiteLabel(ResearchContentClassifier.siteLabel(entity.getUrl()));
    if (video) {
      entity.setLifecycleStatus(SourceLifecycleStatus.PENDING_PARSE.getValue());
    } else {
      entity.setLifecycleStatus(SourceLifecycleStatus.READY.getValue());
    }
    entity.setContentHash(HotNewsCodec.sha256(entity.getSummary()));
    entity.setCreatedAt(LocalDateTime.now());
    itemMapper.insert(entity);
    if (video) {
      videoParseRuns.ensureNone(VideoParseOwnerTypes.SOURCE_ITEM, entity.getId(), entity.getUrl());
    }
    return true;
  }

  /**
   * 收尾抓取运行：更新汇总计数/状态，并重写频道子运行记录。
   *
   * @param runId         抓取运行 ID
   * @param totalItems    总条数
   * @param newItems      新增条数
   * @param dedupedItems  去重条数
   * @param channelRuns   频道级运行视图；null 则只更新主运行
   * @param error         失败原因；null 表示成功
   */
  public void finishRun(
      String runId,
      int totalItems,
      int newItems,
      int dedupedItems,
      List<HotNewsChannelRunView> channelRuns,
      String error) {
    SourceFetchRunEntity run = runMapper.selectById(runId);
    if (run == null) {
      return;
    }
    run.setItemCount(totalItems);
    run.setNewItems(newItems);
    run.setDedupedItems(dedupedItems);
    run.setStatus(error == null ? JobStatus.SUCCEEDED.getValue() : JobStatus.FAILED.getValue());
    run.setErrorMessage(error);
    run.setFinishedAt(LocalDateTime.now());
    runMapper.updateById(run);

    // 先清旧频道运行，再按本次结果重插
    channelRunMapper.delete(new LambdaQueryWrapper<SourceFetchChannelRunEntity>()
        .eq(SourceFetchChannelRunEntity::getFetchRunId, runId));
    if (channelRuns == null) {
      return;
    }
    for (HotNewsChannelRunView view : channelRuns) {
      if (view == null || view.getChannelId() == null) {
        continue;
      }
      SourceFetchChannelRunEntity child = SourceFetchChannelRunEntity.builder()
          .id(StrUtil.blankToDefault(view.getId(), IdsUtil.newId()))
          .fetchRunId(runId)
          .channelId(view.getChannelId())
          .status(StrUtil.blankToDefault(view.getStatus(), ChannelRunStatus.SUCCEEDED.getValue()))
          .itemsCount(view.getItemsCount() == null ? 0 : view.getItemsCount())
          .newItems(view.getNewItems() == null ? 0 : view.getNewItems())
          .errorCode(view.getErrorCode())
          .errorMessage(view.getErrorMessage())
          .startedAt(view.getStartedAt())
          .finishedAt(view.getFinishedAt() == null ? LocalDateTime.now() : view.getFinishedAt())
          .build();
      channelRunMapper.insert(child);
      if (ChannelRunStatus.SUCCEEDED.getValue().equalsIgnoreCase(child.getStatus())) {
        SourceChannelEntity channel = channelMapper.selectById(child.getChannelId());
        if (channel != null) {
          channel.setLastSuccessAt(
              child.getFinishedAt() == null ? LocalDateTime.now() : child.getFinishedAt());
          channel.setUpdatedAt(LocalDateTime.now());
          channelMapper.updateById(channel);
        }
      }
    }
  }

  /**
   * 将抓取运行实体转为对外视图（含频道子运行）。
   *
   * @param run 抓取运行实体
   * @return 运行视图
   */
  private HotNewsFetchRunView fetchRunView(SourceFetchRunEntity run) {
    List<SourceFetchChannelRunEntity> children = channelRunMapper.selectList(
        new LambdaQueryWrapper<SourceFetchChannelRunEntity>()
            .eq(SourceFetchChannelRunEntity::getFetchRunId, run.getId())
            .orderByAsc(SourceFetchChannelRunEntity::getStartedAt));
    // 一次批量加载频道，避免按子运行 selectById
    Map<String, SourceChannelEntity> channelsById = loadChannelsByIds(
        children.stream()
            .map(SourceFetchChannelRunEntity::getChannelId)
            .filter(StrUtil::isNotBlank)
            .distinct()
            .toList());
    List<HotNewsChannelRunView> channelRuns = new ArrayList<>();
    for (SourceFetchChannelRunEntity child : children) {
      SourceChannelEntity channel = channelsById.get(child.getChannelId());
      channelRuns.add(HotNewsChannelRunView.builder()
          .id(child.getId())
          .channelId(child.getChannelId())
          .channelName(channel == null ? child.getChannelId() : channel.getName())
          .channelType(channel == null ? "" : channel.getChannelType())
          .status(child.getStatus())
          .itemsCount(child.getItemsCount())
          .newItems(child.getNewItems())
          .errorCode(child.getErrorCode())
          .errorMessage(child.getErrorMessage())
          .startedAt(child.getStartedAt())
          .finishedAt(child.getFinishedAt())
          .build());
    }
    int newItems = run.getNewItems() == null ? 0 : run.getNewItems();
    int deduped = run.getDedupedItems() == null ? 0 : run.getDedupedItems();
    int total = run.getItemCount() == null ? newItems + deduped : run.getItemCount();
    return HotNewsFetchRunView.builder()
        .id(run.getId())
        .themeId(run.getThemeId())
        .status(run.getStatus())
        .trigger(HotNewsFetchKeys.TRIGGER_MANUAL)
        .requestedChannels(List.of())
        .startedAt(run.getCreatedAt())
        .finishedAt(run.getFinishedAt())
        .totalItems(total)
        .newItems(newItems)
        .dedupedItems(deduped)
        .errorSummary(run.getErrorMessage())
        .channelRuns(channelRuns)
        .jobId(run.getJobId())
        .createdAt(run.getCreatedAt())
        .build();
  }

  /**
   * 按主键批量加载频道，返回 id → 实体。
   *
   * @param channelIds 频道主键列表；空则返回空 Map
   * @return 频道映射
   */
  private Map<String, SourceChannelEntity> loadChannelsByIds(List<String> channelIds) {
    if (channelIds == null || channelIds.isEmpty()) {
      return Map.of();
    }
    Map<String, SourceChannelEntity> byId = new LinkedHashMap<>();
    for (SourceChannelEntity channel : channelMapper.selectList(
        new LambdaQueryWrapper<SourceChannelEntity>().in(SourceChannelEntity::getId, channelIds))) {
      if (channel != null && StrUtil.isNotBlank(channel.getId())) {
        byId.put(channel.getId(), channel);
      }
    }
    return byId;
  }

  /**
   * 将信源条目实体转为对外视图。
   *
   * @param item 信源条目实体
   * @return 条目视图
   */
  private HotNewsItemView itemView(SourceItemEntity item) {
    return HotNewsItemView.builder()
        .id(item.getId())
        .themeId(item.getThemeId())
        .channelId(item.getChannelId())
        .externalId(item.getExternalId())
        .title(item.getTitle())
        .url(item.getUrl())
        .summary(item.getSummary())
        .author(item.getAuthor())
        .publishedAt(item.getPublishedAt())
        .contentHash(item.getContentHash())
        .mediaType(item.getMediaType())
        .lifecycleStatus(item.getLifecycleStatus())
        .discardReason(item.getDiscardReason())
        .contentIntent(item.getContentIntent())
        .build();
  }
}
