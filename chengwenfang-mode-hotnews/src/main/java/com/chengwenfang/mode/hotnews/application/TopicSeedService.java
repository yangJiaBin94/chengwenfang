package com.chengwenfang.mode.hotnews.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.crawler.facade.enums.ChannelType;
import com.chengwenfang.mode.hotnews.domain.ChineseTextNormalizer;
import com.chengwenfang.mode.hotnews.domain.ResearchContentClassifier;
import com.chengwenfang.mode.hotnews.domain.TopicScoreDetails;
import com.chengwenfang.mode.hotnews.domain.entity.TopicClusterEntity;
import com.chengwenfang.mode.hotnews.domain.entity.TopicSourceEntity;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsTopicOriginKinds;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsTopicStatuses;
import com.chengwenfang.mode.hotnews.facade.dto.request.AiThemeSeedPersistRequest;
import com.chengwenfang.mode.hotnews.facade.dto.request.ManualTopicCreateRequest;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsTopicView;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.TopicClusterMapper;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.TopicSourceMapper;
import com.chengwenfang.sourcing.application.HotNewsChannelService;
import com.chengwenfang.sourcing.domain.entity.SourceItemEntity;
import com.chengwenfang.sourcing.facade.dto.request.HotNewsChannelUpsertRequest;
import com.chengwenfang.sourcing.facade.dto.view.HotNewsChannelView;
import com.chengwenfang.sourcing.facade.enums.SourceContentIntent;
import com.chengwenfang.sourcing.facade.enums.SourceLifecycleStatus;
import com.chengwenfang.sourcing.facade.enums.SourceMediaType;
import com.chengwenfang.sourcing.infrastructure.mapper.SourceItemMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 主题种子与手动选题落库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TopicSeedService {

  /** 选题生命周期缺省值。 */
  private static final String DEFAULT_LIFECYCLE_EARLY = "early";

  /** 证据链角色。 */
  private static final String ROLE_EVIDENCE = "evidence";

  /** 手动选题默认写作价值分（偏高，降低被日限额挤掉的概率）。 */
  private static final int MANUAL_WRITING_SCORE = 100;

  /** 选题簇 Mapper。 */
  private final TopicClusterMapper topicMapper;
  /** 选题-信源关联 Mapper。 */
  private final TopicSourceMapper topicSourceMapper;
  /** 信源条目 Mapper。 */
  private final SourceItemMapper itemMapper;
  /** 信源频道服务（手动链接占位渠道）。 */
  private final HotNewsChannelService channels;
  /** 评分明细存储。 */
  private final TopicScoreDetailsStore scoreDetailsStore;
  /** 选题查询（视图组装）。 */
  private final TopicQueryService query;

  /**
   * 落库 AI 主题种子选题（问卷生成素材）；同批次同标题幂等。
   *
   * @param themeId      主题 ID
   * @param radarBatchId 雷达批次 ID
   * @param request      种子标题/摘要/信号等
   * @return 选题视图；参数无效时 null
   */
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
   * 用户手动添加选题：落库为 candidate，可选附带原文链接证据。
   *
   * @param themeId 主题 ID
   * @param request 标题/摘要/链接
   * @return 选题视图
   */
  @Transactional
  public HotNewsTopicView createManualTopic(String themeId, ManualTopicCreateRequest request) {
    if (StrUtil.isBlank(themeId)) {
      throw new BusinessException(ErrorCodes.THEME_NOT_FOUND, "主题不存在", 404);
    }
    if (request == null) {
      throw new BusinessException("invalid_title", "请填写选题标题", 400);
    }
    String title = ChineseTextNormalizer.toSimplified(StrUtil.trim(StrUtil.nullToEmpty(request.getTitle())));
    if (StrUtil.isBlank(title)) {
      throw new BusinessException("invalid_title", "请填写选题标题", 400);
    }
    if (title.length() > 200) {
      title = title.substring(0, 200);
    }
    String summary = ChineseTextNormalizer.toSimplified(
        StrUtil.trim(StrUtil.nullToEmpty(request.getSummary())));
    if (summary.length() > 1200) {
      summary = summary.substring(0, 1200);
    }
    String url = normalizeOptionalUrl(request.getUrl());

    LocalDateTime now = LocalDateTime.now();
    int sourceCount = StrUtil.isNotBlank(url) ? 1 : 0;
    String riskNote = sourceCount == 0
        ? "手动添加且无外链，确认前请自行核实事实"
        : "手动添加并附带原文链接，未自动抓取正文";
    TopicScoreDetails details = TopicScoreDetails.builder()
        .sourceCount(sourceCount)
        .sourceTypes(List.of(HotNewsTopicOriginKinds.MANUAL))
        .signals(List.of(HotNewsTopicOriginKinds.MANUAL_LABEL))
        .metricNote("用户手动添加选题")
        .riskNote(riskNote)
        .originKind(HotNewsTopicOriginKinds.MANUAL)
        .build();
    // 挂到当前最新雷达批次，否则有批次时 listTopics 只查该批次会看不见
    String latestRadarBatchId = resolveLatestRadarBatchId(themeId);
    TopicClusterEntity cluster = TopicClusterEntity.builder()
        .id(IdsUtil.newId())
        .themeId(themeId)
        .title(title)
        .summary(summary)
        .translatedTitle(title)
        .translatedSummary(summary)
        .status(HotNewsTopicStatuses.CANDIDATE)
        .heatScore(50)
        .writingScore(MANUAL_WRITING_SCORE)
        .confidenceScore(60)
        .velocityScore(40)
        .freshnessScore(100)
        .evidenceScore(sourceCount > 0 ? 50 : 20)
        .lifecycle(DEFAULT_LIFECYCLE_EARLY)
        .scoreReason("用户手动添加，待确认选题")
        .firstSeenAt(now)
        .lastSeenAt(now)
        .createdAt(now)
        .updatedAt(now)
        .radarBatchId(StrUtil.blankToDefault(latestRadarBatchId, null))
        .build();
    topicMapper.insert(cluster);
    scoreDetailsStore.write(cluster.getId(), details);

    if (StrUtil.isNotBlank(url)) {
      linkManualSource(themeId, cluster.getId(), title, summary, url, now);
    }

    log.info("手动选题已落库 themeId={} topicId={} hasUrl={}", themeId, cluster.getId(), sourceCount > 0);
    return query.topicView(cluster);
  }

  /**
   * 解析主题下最新雷达批次（与列表池口径一致：radar_rank=1 且未过期）。
   *
   * @param themeId 主题 ID
   * @return 批次 ID；无则空串
   */
  private String resolveLatestRadarBatchId(String themeId) {
    if (StrUtil.isBlank(themeId)) {
      return "";
    }
    TopicClusterEntity batchSeed = topicMapper.selectOne(new LambdaQueryWrapper<TopicClusterEntity>()
        .eq(TopicClusterEntity::getThemeId, themeId)
        .eq(TopicClusterEntity::getRadarRank, 1)
        .isNotNull(TopicClusterEntity::getRadarBatchId)
        .ne(TopicClusterEntity::getRadarBatchId, "")
        .ne(TopicClusterEntity::getStatus, HotNewsTopicStatuses.EXPIRED)
        .orderByDesc(TopicClusterEntity::getUpdatedAt)
        .last("LIMIT 1"));
    return batchSeed == null ? "" : StrUtil.nullToEmpty(batchSeed.getRadarBatchId());
  }

  /**
   * 规范化可选 URL；空白返回空串，非法抛业务异常。
   *
   * @param raw 原始输入
   * @return 规范化 URL 或空串
   */
  private static String normalizeOptionalUrl(String raw) {
    String trimmed = StrUtil.trim(StrUtil.nullToEmpty(raw));
    if (StrUtil.isBlank(trimmed)) {
      return "";
    }
    String lower = trimmed.toLowerCase(Locale.ROOT);
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
      throw new BusinessException(ErrorCodes.INVALID_URL, "请输入有效的链接（以 http:// 或 https:// 开头）", 400);
    }
    return trimmed;
  }

  /**
   * 为手动选题写入信源条目与证据链（不抓取网页）。
   *
   * @param themeId 主题 ID
   * @param topicId 选题 ID
   * @param title   标题
   * @param summary 摘要
   * @param url     已校验链接
   * @param now     当前时间
   */
  private void linkManualSource(
      String themeId,
      String topicId,
      String title,
      String summary,
      String url,
      LocalDateTime now) {
    String channelId = ensureManualChannelId(themeId);
    if (StrUtil.isBlank(channelId)) {
      throw new BusinessException(ErrorCodes.CHANNEL_NOT_FOUND, "无法创建手动选题占位渠道", 500);
    }
    boolean video = ResearchContentClassifier.isVideo(url, title);
    SourceItemEntity item = new SourceItemEntity();
    item.setId(IdsUtil.newId());
    item.setThemeId(themeId);
    item.setChannelId(channelId);
    item.setExternalId(url);
    item.setUrl(url);
    item.setTitle(StrUtil.blankToDefault(title, "未命名来源"));
    item.setSummary(summary);
    item.setMediaType(video ? SourceMediaType.VIDEO.getValue() : SourceMediaType.ARTICLE.getValue());
    item.setSiteLabel(ResearchContentClassifier.siteLabel(url));
    item.setLifecycleStatus(SourceLifecycleStatus.READY.getValue());
    item.setContentIntent(SourceContentIntent.KNOWLEDGE.getValue());
    item.setContentHash(sha256(StrUtil.blankToDefault(summary, url)));
    item.setCreatedAt(now);
    itemMapper.insert(item);

    TopicSourceEntity link = new TopicSourceEntity();
    link.setId(IdsUtil.newId());
    link.setTopicId(topicId);
    link.setSourceItemId(item.getId());
    link.setRole(ROLE_EVIDENCE);
    link.setCreatedAt(now);
    topicSourceMapper.insert(link);
  }

  /**
   * 确保主题下存在禁用的「手动添加」占位渠道。
   *
   * @param themeId 主题 ID
   * @return 渠道 ID；失败为空串
   */
  private String ensureManualChannelId(String themeId) {
    if (StrUtil.isBlank(themeId)) {
      return "";
    }
    String marker = HotNewsTopicOriginKinds.MANUAL_CHANNEL_BASE_URL;
    for (HotNewsChannelView channel : channels.listChannels(themeId)) {
      if (channel == null || StrUtil.isBlank(channel.getId())) {
        continue;
      }
      if (marker.equals(StrUtil.trim(channel.getBaseUrl()))
          || HotNewsTopicOriginKinds.MANUAL_LABEL.equals(StrUtil.trim(channel.getName()))) {
        return channel.getId();
      }
    }
    HotNewsChannelView created = channels.upsertChannel(HotNewsChannelUpsertRequest.builder()
        .themeId(themeId)
        .name(HotNewsTopicOriginKinds.MANUAL_LABEL)
        .type(ChannelType.WEB.getValue())
        .baseUrl(marker)
        .enabled(false)
        .build());
    return created == null ? "" : StrUtil.nullToEmpty(created.getId());
  }

  /**
   * 计算 UTF-8 文本的 SHA-256 十六进制摘要；算法异常时返回空串。
   *
   * @param value 原文
   * @return 小写十六进制 hash，或异常时的 ""
   */
  private static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ignored) {
      return "";
    }
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
