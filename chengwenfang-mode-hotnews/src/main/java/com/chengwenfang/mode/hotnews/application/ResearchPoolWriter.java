package com.chengwenfang.mode.hotnews.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.crawler.facade.FetchFacade;
import com.chengwenfang.crawler.facade.constant.ChannelFetchDefaults;
import com.chengwenfang.crawler.facade.dto.request.FetchRequest;
import com.chengwenfang.crawler.facade.dto.view.FetchResult;
import com.chengwenfang.crawler.facade.dto.view.FetchedItemView;
import com.chengwenfang.crawler.facade.enums.ChannelType;
import com.chengwenfang.job.facade.enums.JobStatus;
import com.chengwenfang.mode.hotnews.application.model.ResearchCandidate;
import com.chengwenfang.mode.hotnews.domain.ChineseTextNormalizer;
import com.chengwenfang.mode.hotnews.domain.ResearchContentClassifier;
import com.chengwenfang.mode.hotnews.domain.TopicResearchPlanner;
import com.chengwenfang.mode.hotnews.domain.entity.TopicClusterEntity;
import com.chengwenfang.mode.hotnews.domain.entity.TopicResearchItemEntity;
import com.chengwenfang.mode.hotnews.domain.entity.TopicResearchRunEntity;
import com.chengwenfang.mode.hotnews.facade.dto.request.ManualResearchItemRequest;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsResearchItemView;
import com.chengwenfang.mode.hotnews.facade.enums.ResearchItemKind;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.TopicClusterMapper;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.TopicResearchItemMapper;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.TopicResearchRunMapper;
import com.chengwenfang.sourcing.facade.constant.VideoParseOwnerTypes;
import com.chengwenfang.sourcing.facade.enums.VideoParseStatus;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * 资料池写入：手动添加素材与挂靠运行。
 */
@Slf4j
@Service
public class ResearchPoolWriter {

  /** 选题簇 Mapper。 */
  private final TopicClusterMapper topicMapper;
  /** 调研运行 Mapper。 */
  private final TopicResearchRunMapper researchRunMapper;
  /** 资料池条目 Mapper。 */
  private final TopicResearchItemMapper researchItemMapper;
  /** 抓取门面。 */
  private final FetchFacade fetchFacade;
  /** 调研 AI 辅助。 */
  private final HotNewsTopicResearchAssistService researchAssist;
  /** 资料打分。 */
  private final TopicResearchItemScoreService researchItemScoreService;
  /** 视频时长/字幕。 */
  private final VideoTranscriptService videoTranscriptService;
  /** 解析状态共享表。 */
  private final VideoParseRunService videoParseRuns;
  /** 选题协作。 */
  private final HotNewsTopicService topics;
  /** 资料评分明细。 */
  private final ResearchItemScoreDetailsStore scoreDetailsStore;
  /** 调研查询（视图映射）。 */
  private final ResearchQueryService query;

  /**
   * @param topicMapper              选题簇 Mapper
   * @param researchRunMapper        调研运行 Mapper
   * @param researchItemMapper       资料池条目 Mapper
   * @param fetchFacade              抓取门面
   * @param researchAssist           调研 AI 辅助
   * @param researchItemScoreService 资料打分
   * @param videoTranscriptService   视频时长/字幕
   * @param videoParseRuns           解析状态共享表
   * @param topics                   选题协作
   * @param scoreDetailsStore        资料评分明细
   * @param query                    调研查询
   */
  public ResearchPoolWriter(
      TopicClusterMapper topicMapper,
      TopicResearchRunMapper researchRunMapper,
      TopicResearchItemMapper researchItemMapper,
      FetchFacade fetchFacade,
      HotNewsTopicResearchAssistService researchAssist,
      TopicResearchItemScoreService researchItemScoreService,
      VideoTranscriptService videoTranscriptService,
      VideoParseRunService videoParseRuns,
      @Lazy HotNewsTopicService topics,
      ResearchItemScoreDetailsStore scoreDetailsStore,
      ResearchQueryService query) {
    this.topicMapper = topicMapper;
    this.researchRunMapper = researchRunMapper;
    this.researchItemMapper = researchItemMapper;
    this.fetchFacade = fetchFacade;
    this.researchAssist = researchAssist;
    this.researchItemScoreService = researchItemScoreService;
    this.videoTranscriptService = videoTranscriptService;
    this.videoParseRuns = videoParseRuns;
    this.topics = topics;
    this.scoreDetailsStore = scoreDetailsStore;
    this.query = query;
  }

  public HotNewsResearchItemView addManualResearchItem(
      String topicId, ManualResearchItemRequest request) {
    topics.requireTopic(topicId);
    String rawUrl = request == null ? null : request.getUrl();
    String url = normalizeManualUrl(rawUrl);
    if (StrUtil.isBlank(url)) {
      throw new BusinessException(ErrorCodes.INVALID_URL, "请输入有效的链接（以 http:// 或 https:// 开头）", 400);
    }
    String urlKey = TopicResearchPlanner.normalizeUrl(url);
    if (StrUtil.isBlank(urlKey)) {
      throw new BusinessException(ErrorCodes.INVALID_URL, "链接格式无法识别，请检查后重试", 400);
    }
    // 同选题去重
    List<TopicResearchItemEntity> existing = researchItemMapper.selectList(
        new LambdaQueryWrapper<TopicResearchItemEntity>()
            .eq(TopicResearchItemEntity::getTopicId, topicId));
    for (TopicResearchItemEntity row : existing) {
      if (urlKey.equals(TopicResearchPlanner.normalizeUrl(row.getUrl()))) {
        throw new BusinessException(ErrorCodes.RESEARCH_ITEM_EXISTS, "资料池中已有相同链接", 409);
      }
    }

    String title = url;
    String summary = "";
    try {
      FetchResult fetched = fetchFacade.fetch(FetchRequest.builder()
          .url(url)
          .type(ChannelType.WEB.getValue())
          .timeoutSeconds(ChannelFetchDefaults.QUICK_WEB_TIMEOUT_SECONDS)
          .build());
      if (fetched != null
          && fetched.isOk()
          && fetched.getItems() != null
          && !fetched.getItems().isEmpty()) {
        FetchedItemView page = fetched.getItems().get(0);
        if (StrUtil.isNotBlank(page.getTitle())) {
          title = page.getTitle().trim();
        }
        if (StrUtil.isNotBlank(page.getSummary())) {
          summary = page.getSummary().trim();
        }
      } else if (fetched != null && StrUtil.isNotBlank(fetched.getErrorMessage())) {
        log.info("手动添加素材抓取未完整成功 url={} err={}", url, fetched.getErrorMessage());
      }
    } catch (Exception exception) {
      log.warn("手动添加素材抓取异常 url={} err={}", url, exception.getMessage());
    }

    ResearchCandidate candidate = ResearchCandidate.builder()
        .title(ChineseTextNormalizer.toSimplified(title))
        .url(url)
        .summary(ChineseTextNormalizer.toSimplified(summary))
        .kind(ResearchItemKind.MANUAL)
        .build();
    // 非中文标题/摘要尽量译成中文
    List<ResearchCandidate> translated = researchAssist.translateNonChinese(List.of(candidate));
    if (translated != null && !translated.isEmpty()) {
      candidate = translated.get(0);
    }
    // 五维 AI 打分（失败回退 kind 默认分）
    TopicClusterEntity topicForScore = topicMapper.selectById(topicId);
    researchItemScoreService.scoreCandidates(
        topicForScore == null ? "" : StrUtil.nullToEmpty(topicForScore.getTitle()),
        topicForScore == null ? "" : StrUtil.nullToEmpty(topicForScore.getSummary()),
        List.of(candidate));

    TopicResearchRunEntity run = ensureManualAnchorRun(topicId);
    LocalDateTime now = LocalDateTime.now();
    TopicResearchItemEntity item = new TopicResearchItemEntity();
    item.setId(IdsUtil.newId());
    item.setRunId(run.getId());
    item.setTopicId(topicId);
    item.setTitle(StrUtil.blankToDefault(candidate.getTitle(), url));
    item.setUrl(url);
    String itemSummary = StrUtil.nullToEmpty(candidate.getSummary());
    item.setSummary(itemSummary.length() > 4000 ? itemSummary.substring(0, 4000) : itemSummary);
    item.setScore(candidate.getScore());
    item.setKind(ResearchItemKind.MANUAL.getValue());
    item.setHasScoreDetails(false);
    // 视频素材探测时长，并在共享表预置未解析状态（失败不阻断添加）
    if (ResearchContentClassifier.isVideo(url, item.getTitle())) {
      item.setVideoDurationSeconds(videoTranscriptService.resolveDurationSeconds(url));
    }
    item.setCreatedAt(now);
    researchItemMapper.insert(item);
    scoreDetailsStore.write(item.getId(), candidate.getScoreDetails());
    if (ResearchContentClassifier.isVideo(url, item.getTitle())) {
      videoParseRuns.ensureNone(
          VideoParseOwnerTypes.TOPIC_RESEARCH_ITEM,
          item.getId(),
          item.getUrl(),
          null,
          item.getVideoDurationSeconds());
      item.setVideoParseStatus(VideoParseStatus.NONE.getValue());
    }
    log.info("手动添加资料完成 topicId={} itemId={} site={}",
        topicId, item.getId(), ResearchContentClassifier.siteLabel(url));
    return query.researchItemView(item);
  }

  /**
   * 规范化手动输入的 URL：补全协议、去空白。
   *
   * @param raw 原始输入
   * @return 规范化 URL；无效时为空串
   */
  private static String normalizeManualUrl(String raw) {
    String text = StrUtil.trim(raw);
    if (StrUtil.isBlank(text)) {
      return "";
    }
    if (!text.contains("://")) {
      text = "https://" + text;
    }
    try {
      URI uri = URI.create(text);
      String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
      if (!"http".equals(scheme) && !"https".equals(scheme)) {
        return "";
      }
      if (StrUtil.isBlank(uri.getHost())) {
        return "";
      }
      return text;
    } catch (Exception ignored) {
      return "";
    }
  }

  /**
   * 为手动素材准备挂靠的调研运行（复用最新运行，否则新建一条已完成运行）。
   *
   * @param topicId 选题 ID
   * @return 运行实体
   */
  private TopicResearchRunEntity ensureManualAnchorRun(String topicId) {
    TopicResearchRunEntity latest = researchRunMapper.selectOne(
        new LambdaQueryWrapper<TopicResearchRunEntity>()
            .eq(TopicResearchRunEntity::getTopicId, topicId)
            .orderByDesc(TopicResearchRunEntity::getCreatedAt)
            .last("LIMIT 1"));
    if (latest != null) {
      return latest;
    }
    LocalDateTime now = LocalDateTime.now();
    TopicResearchRunEntity run = new TopicResearchRunEntity();
    run.setId(IdsUtil.newId());
    run.setTopicId(topicId);
    run.setStatus(JobStatus.SUCCEEDED.getValue());
    run.setCreatedAt(now);
    run.setFinishedAt(now);
    researchRunMapper.insert(run);
    return run;
  }


}
