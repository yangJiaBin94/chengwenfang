package com.workbench.mode.hotnews.application;

import com.workbench.sourcing.application.HotNewsChannelService;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.common.BusinessException;
import com.workbench.common.ErrorCodes;
import com.workbench.common.PageQuery;
import com.workbench.common.SearchQueryNormalizer;
import com.workbench.common.ThemeTitleRelevance;
import com.workbench.mode.hotnews.domain.ChineseTextNormalizer;
import com.workbench.mode.hotnews.domain.TopicMathUtil;
import com.workbench.mode.hotnews.domain.TopicResearchPlanner;
import com.workbench.mode.hotnews.domain.TopicScoreDetails;
import com.workbench.sourcing.domain.entity.SourceChannelEntity;
import com.workbench.sourcing.domain.entity.SourceItemEntity;
import com.workbench.mode.hotnews.domain.entity.TopicClusterEntity;
import com.workbench.mode.hotnews.domain.entity.TopicResearchItemEntity;
import com.workbench.mode.hotnews.domain.entity.TopicSourceEntity;
import com.workbench.mode.hotnews.facade.constant.HotNewsTopicOriginKinds;
import com.workbench.mode.hotnews.facade.constant.HotNewsTopicStatuses;
import com.workbench.mode.hotnews.facade.dto.view.HotNewsTopicPageView;
import com.workbench.mode.hotnews.facade.dto.view.HotNewsTopicView;
import com.workbench.mode.hotnews.facade.enums.HotNewsRadarTopicFilter;
import com.workbench.mode.hotnews.facade.enums.HotNewsRadarTopicSort;
import com.workbench.sourcing.facade.enums.SourceLifecycleStatus;
import com.workbench.sourcing.facade.enums.SourceMediaType;
import com.workbench.sourcing.infrastructure.mapper.SourceChannelMapper;
import com.workbench.sourcing.infrastructure.mapper.SourceItemMapper;
import com.workbench.mode.hotnews.infrastructure.mapper.TopicClusterMapper;
import com.workbench.mode.hotnews.infrastructure.mapper.TopicResearchItemMapper;
import com.workbench.mode.hotnews.infrastructure.mapper.TopicSourceMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 选题查询：列表、分页、已确认素材去重键、视图组装与主题相关性信号。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TopicQueryService {

  /** 雷达检索缺省来源标签。 */
  private static final String DEFAULT_PUBLIC_SOURCE_LABEL = "公开来源";

  /** 选题生命周期缺省值。 */
  private static final String DEFAULT_LIFECYCLE_EARLY = "early";

  /** 选题簇 Mapper。 */
  private final TopicClusterMapper topicMapper;
  /** 选题-信源关联 Mapper。 */
  private final TopicSourceMapper topicSourceMapper;
  /** 资料池 Mapper。 */
  private final TopicResearchItemMapper researchItemMapper;
  /** 信源条目 Mapper。 */
  private final SourceItemMapper itemMapper;
  /** 频道 Mapper。 */
  private final SourceChannelMapper channelMapper;
  /** 频道配置解析。 */
  private final HotNewsChannelService channels;
  /** 评分明细存储。 */
  private final TopicScoreDetailsStore scoreDetailsStore;
  /** 选题聚合能力。 */
  private final HotNewsTopicAggregateService aggregate;

  public List<HotNewsTopicView> listTopics(String themeId, int limit) {
    // status=null 时重载内默认排除 expired
    return listTopics(themeId, null, limit);
  }

  /**
   * 按状态筛选选题列表；status 为空时排除 expired。按 writingScore、updatedAt 倒序。
   *
   * @param themeId 主题 ID；为 null 时不过滤主题
   * @param status  选题状态；空白则 ne expired，否则精确匹配
   * @param limit   期望条数，实际取 {@code max(1, min(limit, 100))}
   * @return 选题视图列表
   */
  @Transactional
  public List<HotNewsTopicView> listTopics(String themeId, String status, int limit) {
    int capped = Math.max(1, Math.min(limit, 100));
    // 在 refresh 改 updatedAt 之前锁定「最新批次」：取 radar_rank=1 的最新一条
    String latestRadarBatchId = null;
    if (StrUtil.isNotBlank(themeId) && (StrUtil.isBlank(status))) {
      TopicClusterEntity batchSeed = topicMapper.selectOne(new LambdaQueryWrapper<TopicClusterEntity>()
          .eq(TopicClusterEntity::getThemeId, themeId)
          .eq(TopicClusterEntity::getRadarRank, 1)
          .isNotNull(TopicClusterEntity::getRadarBatchId)
          .ne(TopicClusterEntity::getRadarBatchId, "")
          .orderByDesc(TopicClusterEntity::getUpdatedAt)
          .last("LIMIT 1"));
      if (batchSeed != null) {
        latestRadarBatchId = batchSeed.getRadarBatchId();
      }
    }
    // 列表前先清掉过期未确认素材，保证「只在当天生效」
    if (StrUtil.isNotBlank(themeId)) {
      aggregate.applyDailyTopicLimit(themeId);
      aggregate.refreshTopicScores(themeId);
    }
    // 有最新雷达批次时，默认按写作分取本轮前 N 条（高分在前）
    if (StrUtil.isNotBlank(latestRadarBatchId)) {
      int topLimit = Math.min(capped, TopicMathUtil.DAILY_TOPIC_LIMIT);
      List<TopicClusterEntity> top = topicMapper.selectList(new LambdaQueryWrapper<TopicClusterEntity>()
          .eq(TopicClusterEntity::getThemeId, themeId)
          .eq(TopicClusterEntity::getRadarBatchId, latestRadarBatchId)
          .ne(TopicClusterEntity::getStatus, HotNewsTopicStatuses.EXPIRED)
          .orderByDesc(TopicClusterEntity::getWritingScore)
          .orderByDesc(TopicClusterEntity::getUpdatedAt)
          .orderByAsc(TopicClusterEntity::getTitle)
          .orderByAsc(TopicClusterEntity::getId)
          .last("LIMIT " + topLimit));
      if (!top.isEmpty()) {
        return top.stream().map(this::topicView).toList();
      }
    }
    LambdaQueryWrapper<TopicClusterEntity> query = new LambdaQueryWrapper<TopicClusterEntity>()
        .eq(themeId != null, TopicClusterEntity::getThemeId, themeId)
        .orderByDesc(TopicClusterEntity::getWritingScore)
        .orderByDesc(TopicClusterEntity::getUpdatedAt)
        .orderByAsc(TopicClusterEntity::getTitle)
        .orderByAsc(TopicClusterEntity::getId)
        .last("LIMIT " + capped);
    if (StrUtil.isNotBlank(status)) {
      query.eq(TopicClusterEntity::getStatus, status);
    } else {
      // 列表默认不展示已过期选题
      query.ne(TopicClusterEntity::getStatus, HotNewsTopicStatuses.EXPIRED);
    }
    return topicMapper.selectList(query).stream().map(this::topicView).toList();
  }

  /**
   * 分页列出热点雷达选题：先取雷达池（最新批次 TopN 或回退列表），再按筛选/关键字/排序切片。
   *
   * @param themeId 主题 ID
   * @param query   分页与关键字；null 时按默认页码页大小
   * @param filter  筛选词；空白默认 today
   * @param sortBy  排序词；空白默认 writing
   * @return 当前页与雷达池级候选/总数统计
   */
  @Transactional
  public HotNewsTopicPageView pageTopics(
      String themeId, PageQuery query, String filter, String sortBy) {
    PageQuery normalized = query == null ? new PageQuery().normalized() : query.normalized();
    HotNewsRadarTopicFilter topicFilter = HotNewsRadarTopicFilter.from(filter);
    HotNewsRadarTopicSort topicSort = HotNewsRadarTopicSort.from(sortBy);

    // 复用雷达池装载（含日限额与评分补算）
    List<HotNewsTopicView> pool = listTopics(themeId, null, 100);
    long candidateCount = pool.stream()
        .filter(topic -> HotNewsTopicStatuses.CANDIDATE.equals(topic.getStatus()))
        .count();
    long topicCount = pool.size();

    String keyword = StrUtil.trim(normalized.getKeyword());
    List<HotNewsTopicView> filtered = pool.stream()
        .filter(this::isRadarListVisible)
        .filter(topic -> matchesRadarTopicFilter(topic, topicFilter))
        .filter(topic -> matchesRadarTopicKeyword(topic, keyword))
        .sorted((left, right) -> compareRadarTopics(left, right, topicSort))
        .toList();

    long total = filtered.size();
    List<HotNewsTopicView> items = List.of();
    if (total > 0) {
      int offset = normalized.offset();
      // 页码超出时夹到最后一页，避免空页
      if (offset >= total) {
        int lastPage = (int) Math.max(1, (total + normalized.getPageSize() - 1) / normalized.getPageSize());
        normalized = PageQuery.builder()
            .page(lastPage)
            .pageSize(normalized.getPageSize())
            .keyword(normalized.getKeyword())
            .build()
            .normalized();
        offset = normalized.offset();
      }
      int end = (int) Math.min(total, (long) offset + normalized.getPageSize());
      items = filtered.subList(offset, end);
    }

    return HotNewsTopicPageView.builder()
        .items(items)
        .total(total)
        .page(normalized.getPage())
        .pageSize(normalized.getPageSize())
        .candidateCount(candidateCount)
        .topicCount(topicCount)
        .build();
  }

  /**
   * 列出已确认选题，按确认时间倒序，不截成当日雷达条数。
   *
   * @param themeId 主题 ID
   * @return 已确认 / 写作中 / 已完成选题
   */
  public List<HotNewsTopicView> listConfirmedTopics(String themeId) {
    return topicMapper.selectList(new LambdaQueryWrapper<TopicClusterEntity>()
            .eq(StrUtil.isNotBlank(themeId), TopicClusterEntity::getThemeId, themeId)
            .in(TopicClusterEntity::getStatus, HotNewsTopicStatuses.CONFIRMED_LIFECYCLE)
            .orderByDesc(TopicClusterEntity::getConfirmedAt)
            .orderByDesc(TopicClusterEntity::getUpdatedAt)
            .last("LIMIT 500"))
        .stream()
        .map(this::topicView)
        .toList();
  }

  /**
   * 批量汇总已选题资料池与关联信源的去重键，供雷达采集排除重复素材。
   *
   * @param themeId 主题 ID
   * @return 去重键集合；主题空白或无已选题时为空集
   */
  public Set<String> listConfirmedTopicMaterialDedupeKeys(String themeId) {
    if (StrUtil.isBlank(themeId)) {
      return Set.of();
    }
    // 先取已选题 ID，再批量拉资料与信源，避免按选题 N+1
    List<String> topicIds = topicMapper.selectList(new LambdaQueryWrapper<TopicClusterEntity>()
            .eq(TopicClusterEntity::getThemeId, themeId)
            .in(TopicClusterEntity::getStatus, HotNewsTopicStatuses.CONFIRMED_LIFECYCLE)
            .select(TopicClusterEntity::getId))
        .stream()
        .map(TopicClusterEntity::getId)
        .filter(StrUtil::isNotBlank)
        .toList();
    if (topicIds.isEmpty()) {
      return Set.of();
    }
    LinkedHashSet<String> keys = new LinkedHashSet<>();
    List<TopicResearchItemEntity> researchItems = researchItemMapper.selectList(
        new LambdaQueryWrapper<TopicResearchItemEntity>()
            .in(TopicResearchItemEntity::getTopicId, topicIds)
            .select(TopicResearchItemEntity::getUrl, TopicResearchItemEntity::getTitle));
    for (TopicResearchItemEntity item : researchItems) {
      if (item == null) {
        continue;
      }
      String key = TopicResearchPlanner.materialDedupeKey(item.getUrl(), item.getTitle());
      if (TopicResearchPlanner.isMeaningfulMaterialDedupeKey(key)) {
        keys.add(key);
      }
    }
    List<String> sourceItemIds = topicSourceMapper.selectList(
            new LambdaQueryWrapper<TopicSourceEntity>()
                .in(TopicSourceEntity::getTopicId, topicIds)
                .select(TopicSourceEntity::getSourceItemId))
        .stream()
        .map(TopicSourceEntity::getSourceItemId)
        .filter(StrUtil::isNotBlank)
        .distinct()
        .toList();
    if (sourceItemIds.isEmpty()) {
      return keys;
    }
    List<SourceItemEntity> sourceItems = itemMapper.selectList(
        new LambdaQueryWrapper<SourceItemEntity>()
            .in(SourceItemEntity::getId, sourceItemIds)
            .select(
                SourceItemEntity::getUrl,
                SourceItemEntity::getTitle,
                SourceItemEntity::getLifecycleStatus));
    for (SourceItemEntity item : sourceItems) {
      if (item == null
          || SourceLifecycleStatus.from(item.getLifecycleStatus()).shouldSkipAggregate()) {
        continue;
      }
      String key = TopicResearchPlanner.materialDedupeKey(item.getUrl(), item.getTitle());
      if (TopicResearchPlanner.isMeaningfulMaterialDedupeKey(key)) {
        keys.add(key);
      }
    }
    return keys;
  }

  /**
   * 获取单个选题详情。
   *
   * @param topicId 选题主键
   * @return 选题视图
   * @throws BusinessException 选题不存在时（TOPIC_NOT_FOUND / 404）
   */
  public HotNewsTopicView getTopic(String topicId) {
    return topicView(requireTopic(topicId));
  }

  private static boolean isConfirmedTopicStatus(String status) {
    return HotNewsTopicStatuses.isConfirmedLifecycle(status);
  }

  /**
   * 雷达列表是否展示该选题：非当日确认的已选题只在「已选题」页出现。
   *
   * @param topic 选题视图
   * @return 可展示时为 true
   */
  private boolean isRadarListVisible(HotNewsTopicView topic) {
    if (topic == null) {
      return false;
    }
    if (!isConfirmedTopicStatus(topic.getStatus())) {
      return true;
    }
    LocalDateTime confirmedAt =
        topic.getConfirmedAt() != null ? topic.getConfirmedAt() : topic.getUpdatedAt();
    return isBeijingCalendarToday(confirmedAt);
  }

  /**
   * 雷达筛选条件是否命中。
   *
   * @param topic  选题视图
   * @param filter 筛选枚举
   * @return 命中为 true
   */
  private boolean matchesRadarTopicFilter(HotNewsTopicView topic, HotNewsRadarTopicFilter filter) {
    if (topic == null) {
      return false;
    }
    if (filter == null || filter.isToday()) {
      LocalDateTime seen =
          topic.getCreatedAt() != null ? topic.getCreatedAt() : topic.getLastSeenAt();
      return isBeijingCalendarToday(seen);
    }
    if (filter.isCandidate()) {
      return HotNewsTopicStatuses.CANDIDATE.equals(topic.getStatus());
    }
    if (filter.isConfirmed()) {
      return isConfirmedTopicStatus(topic.getStatus());
    }
    return true;
  }

  /**
   * 关键字是否命中标题、摘要或来源标签。
   *
   * @param topic   选题视图
   * @param keyword 已 trim 的关键字；空白则不过滤
   * @return 命中为 true
   */
  private boolean matchesRadarTopicKeyword(HotNewsTopicView topic, String keyword) {
    if (StrUtil.isBlank(keyword)) {
      return true;
    }
    if (topic == null) {
      return false;
    }
    String haystack = (StrUtil.nullToEmpty(topic.getTitle())
            + " "
            + StrUtil.nullToEmpty(topic.getSummary())
            + " "
            + radarTopicSearchLabels(topic))
        .toLowerCase(Locale.ROOT);
    return haystack.contains(keyword.toLowerCase(Locale.ROOT));
  }

  /**
   * 拼出来源相关检索标签（站点、媒体、来源形态）。
   *
   * @param topic 选题视图
   * @return 空格分隔标签
   */
  private String radarTopicSearchLabels(HotNewsTopicView topic) {
    if (topic == null) {
      return "";
    }
    if (HotNewsTopicOriginKinds.isAiThemeSeed(topic.getOriginKind())) {
      return HotNewsTopicOriginKinds.AI_THEME_SEED_LABEL;
    }
    List<String> labels = new ArrayList<>();
    if (topic.getSiteLabels() != null) {
      labels.addAll(topic.getSiteLabels().stream().filter(StrUtil::isNotBlank).toList());
    }
    if (topic.getMediaTypes() != null) {
      labels.addAll(topic.getMediaTypes().stream().filter(StrUtil::isNotBlank).toList());
    }
    if (!labels.isEmpty()) {
      return String.join(" ", labels);
    }
    TopicScoreDetails details = topic.getScoreDetails();
    if (details != null && details.getSourceTypes() != null && !details.getSourceTypes().isEmpty()) {
      return String.join(" ", details.getSourceTypes());
    }
    return DEFAULT_PUBLIC_SOURCE_LABEL;
  }

  /**
   * 雷达选题比较：主分 → 热度 → 置信度 → 最近出现时间。
   *
   * @param left  左选题
   * @param right 右选题
   * @param sort  排序维度
   * @return 比较结果（降序）
   */
  private int compareRadarTopics(
      HotNewsTopicView left, HotNewsTopicView right, HotNewsRadarTopicSort sort) {
    int leftPrimary = sort != null && sort.isHeat()
        ? nullToZero(left.getHeatScore())
        : nullToZero(left.getWritingScore());
    int rightPrimary = sort != null && sort.isHeat()
        ? nullToZero(right.getHeatScore())
        : nullToZero(right.getWritingScore());
    int byPrimary = Integer.compare(rightPrimary, leftPrimary);
    if (byPrimary != 0) {
      return byPrimary;
    }
    int byHeat = Integer.compare(nullToZero(right.getHeatScore()), nullToZero(left.getHeatScore()));
    if (byHeat != 0) {
      return byHeat;
    }
    int byConfidence =
        Integer.compare(nullToZero(right.getConfidenceScore()), nullToZero(left.getConfidenceScore()));
    if (byConfidence != 0) {
      return byConfidence;
    }
    LocalDateTime leftSeen = left.getLastSeenAt();
    LocalDateTime rightSeen = right.getLastSeenAt();
    if (leftSeen == null && rightSeen == null) {
      return 0;
    }
    if (leftSeen == null) {
      return 1;
    }
    if (rightSeen == null) {
      return -1;
    }
    return rightSeen.compareTo(leftSeen);
  }

  /**
   * 判断时间是否落在东八区「今天」日历日（按落库墙钟日期比较）。
   *
   * @param value 本地日期时间；null 视为否
   * @return 是当天则为 true
   */
  private boolean isBeijingCalendarToday(LocalDateTime value) {
    if (value == null) {
      return false;
    }
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    return value.toLocalDate().equals(today);
  }

  /**
   * 空整数按 0。
   *
   * @param value 可空整数
   * @return 非空值或 0
   */
  private static int nullToZero(Integer value) {
    return value == null ? 0 : value;
  }

  public TopicClusterEntity requireTopic(String topicId) {
    TopicClusterEntity topic = topicMapper.selectById(topicId);
    if (topic == null) {
      throw new BusinessException(ErrorCodes.TOPIC_NOT_FOUND, "选题不存在", 404);
    }
    return topic;
  }

  /**
   * 组装主题标题相关性信号：聚合主题下渠道搜索词（优先当前渠道）。
   *
   * @param themeId   主题 ID
   * @param channelId 当前渠道；可空
   * @return Profile；无查询词时为空信号（过滤放行）
   */
  public ThemeTitleRelevance.Profile themeRelevanceProfile(String themeId, String channelId) {
    List<String> queries = new ArrayList<>();
    if (StrUtil.isNotBlank(channelId)) {
      SourceChannelEntity channel = channelMapper.selectById(channelId);
      if (channel != null) {
        queries.addAll(channels.parseConfig(channel).resolveQueries());
      }
    }
    if (queries.isEmpty() && StrUtil.isNotBlank(themeId)) {
      List<SourceChannelEntity> themeChannels = channelMapper.selectList(
          new LambdaQueryWrapper<SourceChannelEntity>()
              .eq(SourceChannelEntity::getThemeId, themeId)
              .eq(SourceChannelEntity::getEnabled, true));
      for (SourceChannelEntity channel : themeChannels) {
        if (channel == null) {
          continue;
        }
        queries.addAll(channels.parseConfig(channel).resolveQueries());
      }
    }
    return new ThemeTitleRelevance.Profile("", SearchQueryNormalizer.normalize(queries), List.of());
  }

  /**
   * 选题实体转对外视图（含来源站点/媒体标签与发布时间区间）。
   *
   * @param topic 选题实体
   * @return 选题视图
   */
  public HotNewsTopicView topicView(TopicClusterEntity topic) {
    List<TopicSourceEntity> links = topicSourceMapper.selectList(new LambdaQueryWrapper<TopicSourceEntity>()
        .eq(TopicSourceEntity::getTopicId, topic.getId()));
    // 列表展示时补齐空摘要，避免卡片仍显示「这是一条热点线索」占位
    String before = StrUtil.nullToEmpty(topic.getSummary());
    aggregate.fillClusterSummaryFromMemberBriefs(topic, links);
    if (!StrUtil.nullToEmpty(topic.getSummary()).equals(before)) {
      topicMapper.updateById(topic);
    }
    String title = ChineseTextNormalizer.toSimplified(
        StrUtil.blankToDefault(topic.getTranslatedTitle(), topic.getTitle()));
    String summary = ChineseTextNormalizer.toSimplified(
        StrUtil.isNotBlank(topic.getTranslatedSummary())
            ? topic.getTranslatedSummary()
            : topic.getSummary());
    TopicScoreDetails details = scoreDetailsStore.read(topic.getId());
    List<String> siteLabels = new ArrayList<>();
    List<String> mediaTypes = new ArrayList<>();
    LocalDateTime publishFrom = null;
    LocalDateTime publishTo = null;
    for (TopicSourceEntity link : links) {
      if (link == null) {
        continue;
      }
      SourceItemEntity item = itemMapper.selectById(link.getSourceItemId());
      if (item == null) {
        continue;
      }
      String site = HotNewsCodec.resolveSiteLabel(item.getUrl(), item.getSiteLabel());
      if (StrUtil.isNotBlank(site) && !siteLabels.contains(site)) {
        siteLabels.add(site);
      }
      String media = HotNewsCodec.resolveMediaType(item.getUrl(), item.getTitle(), item.getMediaType());
      String mediaLabel = SourceMediaType.VIDEO.getValue().equals(media) ? "视频" : "文章";
      if (!mediaTypes.contains(mediaLabel)) {
        mediaTypes.add(mediaLabel);
      }
      LocalDateTime published = item.getPublishedAt();
      if (published == null) {
        continue;
      }
      if (publishFrom == null || published.isBefore(publishFrom)) {
        publishFrom = published;
      }
      if (publishTo == null || published.isAfter(publishTo)) {
        publishTo = published;
      }
    }
    return HotNewsTopicView.builder()
        .id(topic.getId())
        .themeId(topic.getThemeId())
        .title(title)
        .summary(summary)
        .status(topic.getStatus())
        .heatScore(topic.getHeatScore())
        .writingScore(topic.getWritingScore())
        .confidenceScore(topic.getConfidenceScore())
        .velocityScore(topic.getVelocityScore() == null ? 0 : topic.getVelocityScore())
        .freshnessScore(topic.getFreshnessScore() == null ? 0 : topic.getFreshnessScore())
        .evidenceScore(topic.getEvidenceScore() == null
            ? (topic.getConfidenceScore() == null ? 0 : topic.getConfidenceScore())
            : topic.getEvidenceScore())
        .lifecycle(StrUtil.blankToDefault(topic.getLifecycle(), DEFAULT_LIFECYCLE_EARLY))
        .scoreDetails(details)
        .scoreReason(topic.getScoreReason())
        .sourceCount((long) links.size())
        .firstSeenAt(topic.getFirstSeenAt())
        .lastSeenAt(topic.getLastSeenAt())
        .publishFrom(publishFrom)
        .publishTo(publishTo)
        .createdAt(topic.getCreatedAt())
        .updatedAt(topic.getUpdatedAt())
        .confirmedAt(topic.getConfirmedAt())
        .radarBatchId(topic.getRadarBatchId())
        .radarRank(topic.getRadarRank())
        .siteLabels(siteLabels)
        .mediaTypes(mediaTypes)
        .originKind(details == null ? null : details.getOriginKind())
        .build();
  }


}
