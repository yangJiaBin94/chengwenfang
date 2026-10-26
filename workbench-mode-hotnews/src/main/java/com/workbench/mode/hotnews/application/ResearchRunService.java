package com.workbench.mode.hotnews.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.common.BusinessException;
import com.workbench.common.ErrorCodes;
import com.workbench.common.IdsUtil;
import com.workbench.crawler.facade.FetchFacade;
import com.workbench.crawler.facade.MarketingPageFilter;
import com.workbench.crawler.facade.constant.ChannelFetchDefaults;
import com.workbench.crawler.facade.dto.request.FetchRequest;
import com.workbench.crawler.facade.dto.view.FetchResult;
import com.workbench.crawler.facade.dto.view.FetchedItemView;
import com.workbench.crawler.facade.dto.view.SearchHitView;
import com.workbench.crawler.facade.enums.ChannelType;
import com.workbench.job.facade.JobExecutionContext;
import com.workbench.job.facade.JobFacade;
import com.workbench.job.facade.constant.JobEntityTypes;
import com.workbench.job.facade.constant.JobTypes;
import com.workbench.job.facade.dto.job.HotNewsTopicResearchJobInput;
import com.workbench.job.facade.dto.view.JobView;
import com.workbench.job.facade.enums.JobStatus;
import com.workbench.mode.hotnews.application.model.ResearchCandidate;
import com.workbench.mode.hotnews.application.research.ResearchKeywordPlan;
import com.workbench.mode.hotnews.domain.ChineseTextNormalizer;
import com.workbench.mode.hotnews.domain.TopicResearchPlanner;
import com.workbench.mode.hotnews.domain.entity.TopicBriefEntity;
import com.workbench.mode.hotnews.domain.entity.TopicClusterEntity;
import com.workbench.mode.hotnews.domain.entity.TopicResearchRunEntity;
import com.workbench.mode.hotnews.facade.dto.request.VideoParseRequest;
import com.workbench.mode.hotnews.facade.dto.view.HotNewsResearchCompleteView;
import com.workbench.mode.hotnews.facade.dto.view.HotNewsTopicSourceView;
import com.workbench.mode.hotnews.facade.enums.ResearchItemKind;
import com.workbench.mode.hotnews.infrastructure.mapper.TopicBriefMapper;
import com.workbench.mode.hotnews.infrastructure.mapper.TopicResearchRunMapper;
import com.workbench.sourcing.domain.entity.SourceChannelEntity;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 调研运行：启动/完成资料补充，视频解析入口委托。
 */
@Slf4j
@Service
public class ResearchRunService {

  /** 调研运行 Mapper。 */
  private final TopicResearchRunMapper researchRunMapper;
  /** 选题 brief Mapper。 */
  private final TopicBriefMapper briefMapper;
  /** 任务门面。 */
  private final JobFacade jobs;
  /** 抓取门面。 */
  private final FetchFacade fetchFacade;
  /** 调研 AI 辅助。 */
  private final HotNewsTopicResearchAssistService researchAssist;
  /** 资料打分。 */
  private final TopicResearchItemScoreService researchItemScoreService;
  /** 资料池事务替换。 */
  private final HotNewsTopicResearchPoolTx researchPoolTx;
  /** 视频时长（超长过滤）。 */
  private final VideoTranscriptService videoTranscriptService;
  /** 视频解析协作。 */
  private final HotNewsVideoParseService videoParse;
  /** 选题协作。 */
  private final HotNewsTopicService topics;
  /** 候选采集。 */
  private final ResearchCandidateCollector collector;

  /**
   * @param researchRunMapper        调研运行 Mapper
   * @param briefMapper              选题 brief Mapper
   * @param jobs                     任务门面
   * @param fetchFacade              抓取门面
   * @param researchAssist           调研 AI 辅助
   * @param researchItemScoreService 资料打分
   * @param researchPoolTx           资料池事务替换
   * @param videoTranscriptService   视频时长
   * @param videoParse               视频解析协作
   * @param topics                   选题协作
   * @param collector                候选采集
   */
  public ResearchRunService(
      TopicResearchRunMapper researchRunMapper,
      TopicBriefMapper briefMapper,
      JobFacade jobs,
      FetchFacade fetchFacade,
      HotNewsTopicResearchAssistService researchAssist,
      TopicResearchItemScoreService researchItemScoreService,
      HotNewsTopicResearchPoolTx researchPoolTx,
      VideoTranscriptService videoTranscriptService,
      HotNewsVideoParseService videoParse,
      @Lazy HotNewsTopicService topics,
      ResearchCandidateCollector collector) {
    this.researchRunMapper = researchRunMapper;
    this.briefMapper = briefMapper;
    this.jobs = jobs;
    this.fetchFacade = fetchFacade;
    this.researchAssist = researchAssist;
    this.researchItemScoreService = researchItemScoreService;
    this.researchPoolTx = researchPoolTx;
    this.videoTranscriptService = videoTranscriptService;
    this.videoParse = videoParse;
    this.topics = topics;
    this.collector = collector;
  }

  public JobView startTopicResearch(String topicId) {
    // 先校验选题存在，避免空跑任务
    topics.requireTopic(topicId);
    TopicResearchRunEntity run = new TopicResearchRunEntity();
    run.setId(IdsUtil.newId());
    run.setTopicId(topicId);
    run.setStatus(JobStatus.PENDING.getValue());
    run.setCreatedAt(LocalDateTime.now());
    JobView job = jobs.create(
        JobTypes.HOT_NEWS_TOPIC_RESEARCH,
        JobEntityTypes.TOPIC_RESEARCH_RUN,
        run.getId(),
        HotNewsTopicResearchJobInput.builder()
            .topicId(topicId)
            .researchRunId(run.getId())
            .build());
    run.setJobId(job.getId());
    researchRunMapper.insert(run);
    return job;
  }

  /**
   * 启动资料池视频解析任务。
   *
   * @param topicId 选题主键
   * @param request 条目与起始步骤
   * @return 任务视图
   */
  @Transactional
  public JobView startVideoParse(String topicId, VideoParseRequest request) {
    return videoParse.startVideoParse(topicId, request);
  }

  /**
   * 执行视频解析 Job。
   *
   * @param jobId     任务 ID
   * @param inputJson 输入 JSON
   * @param context   进度上下文
   */
  public void executeVideoParseJob(String jobId, String inputJson, JobExecutionContext context) {
    videoParse.executeVideoParseJob(jobId, inputJson, context);
  }

  /**
   * 为视频候选写入时长秒数。
   *
   * @param candidates 待入库候选
   */
  public void stampVideoDurations(List<ResearchCandidate> candidates) {
    videoParse.stampVideoDurations(candidates);
  }

  /**
   * 为待解析雷达视频入队自动解析任务。
   *
   * @param themeId 主题 ID
   * @return 任务；无需解析时 null
   */
  @Transactional
  public JobView enqueuePendingSourceVideoParse(String themeId) {
    return videoParse.enqueuePendingSourceVideoParse(themeId);
  }

  /**
   * 执行雷达信源视频解析 Job。
   *
   * @param jobId     任务 ID
   * @param inputJson 输入
   * @param context   进度上下文
   */
  public void executeSourceVideoParseJob(String jobId, String inputJson, JobExecutionContext context) {
    videoParse.executeSourceVideoParseJob(jobId, inputJson, context);
  }

  public HotNewsResearchCompleteView completeTopicResearch(String researchRunId, String topicId) {
    TopicResearchRunEntity run = researchRunMapper.selectById(researchRunId);
    if (run == null) {
      throw new BusinessException(ErrorCodes.RESEARCH_RUN_NOT_FOUND, "调研运行不存在", 404);
    }
    // 允许调用方覆盖 topicId；未传则用运行记录
    String resolvedTopicId = StrUtil.isBlank(topicId) ? run.getTopicId() : topicId;
    TopicClusterEntity topic = topics.requireTopic(resolvedTopicId);
    LocalDateTime now = LocalDateTime.now();
    run.setStatus(JobStatus.RUNNING.getValue());
    researchRunMapper.updateById(run);

    // 先完成检索/AI（不改资料池）；最后用短事务原子替换，避免检索失败导致空池
    // 选题原始来源：固定入资料池；检索时排除同 URL，避免网页结果重复塞入
    List<HotNewsTopicSourceView> sources = topics.listTopicSources(resolvedTopicId);
    List<ResearchCandidate> sourceCandidates = new ArrayList<>();
    Set<String> sourceUrls = new LinkedHashSet<>();
    for (HotNewsTopicSourceView source : sources) {
      String key = TopicResearchPlanner.normalizeUrl(source.getUrl());
      if (StrUtil.isNotBlank(key) && !sourceUrls.add(key)) {
        continue;
      }
      String sourceTitle = StrUtil.isBlank(source.getTitle())
          ? (source.getUrl() == null ? "未命名来源" : source.getUrl())
          : source.getTitle();
      // 原始来源也剔除商城商品/推销页，避免资料池被导购污染
      if (MarketingPageFilter.shouldSkip(source.getUrl(), sourceTitle)) {
        continue;
      }
      sourceCandidates.add(ResearchCandidate.builder()
          .title(ChineseTextNormalizer.toSimplified(sourceTitle))
          .url(source.getUrl())
          .summary("")
          .kind(ResearchItemKind.SOURCE)
          .build());
    }

    String topicTitle = topic.getTitle() == null ? "" : topic.getTitle().trim();
    String summary = topic.getSummary() == null ? "" : topic.getSummary().trim();
    // AI 提取检索主体/关键词（失败回退规则），再按主题已配置搜索渠道检索
    ResearchKeywordPlan keywordPlan = researchAssist.extractKeywords(topicTitle, summary);
    List<String> queries = researchAssist.buildSearchQueries(keywordPlan);
    List<SourceChannelEntity> searchChannels = collector.listResearchSearchChannels(topic.getThemeId());
    log.info("选题资料补充检索 subject={} fromAi={} channels={} queries={} sourceItems={}",
        keywordPlan.getSubject(),
        keywordPlan.isFromAi(),
        searchChannels.stream().map(SourceChannelEntity::getName).toList(),
        queries,
        sourceCandidates.size());

    List<ResearchCandidate> webCollected = new ArrayList<>();
    Set<String> seenUrls = new LinkedHashSet<>(sourceUrls);
    int fetchBudget = 8;
    for (String query : queries) {
      List<SearchHitView> hits = collector.searchViaConfiguredChannels(searchChannels, query, 5);
      for (SearchHitView hit : hits) {
        if (hit == null || StrUtil.isBlank(hit.getUrl())) {
          continue;
        }
        String urlKey = TopicResearchPlanner.normalizeUrl(hit.getUrl());
        if (StrUtil.isBlank(urlKey) || !seenUrls.add(urlKey)) {
          continue;
        }
        if (MarketingPageFilter.shouldSkip(hit.getUrl(), hit.getTitle())) {
          continue;
        }
        String itemSummary = hit.getSnippet() == null ? "" : hit.getSnippet();
        String title = StrUtil.isBlank(hit.getTitle()) ? hit.getUrl() : hit.getTitle();
        if (fetchBudget > 0) {
          fetchBudget--;
          try {
            FetchResult fetched = fetchFacade.fetch(FetchRequest.builder()
                .url(hit.getUrl())
                .type(ChannelType.WEB.getValue())
                .timeoutSeconds(ChannelFetchDefaults.QUICK_WEB_TIMEOUT_SECONDS)
                .build());
            if (fetched.isOk() && fetched.getItems() != null && !fetched.getItems().isEmpty()) {
              FetchedItemView item = fetched.getItems().get(0);
              if (StrUtil.isNotBlank(item.getTitle())) {
                title = item.getTitle();
              }
              if (StrUtil.isNotBlank(item.getSummary())) {
                itemSummary = item.getSummary();
              }
            }
          } catch (Exception ignored) {
            // 保留搜索摘要
          }
        }
        // 抓取后标题可能变成商品名，再滤一次
        if (MarketingPageFilter.shouldSkip(hit.getUrl(), title)) {
          continue;
        }
        webCollected.add(ResearchCandidate.builder()
            .title(ChineseTextNormalizer.toSimplified(title))
            .url(hit.getUrl())
            .summary(ChineseTextNormalizer.toSimplified(itemSummary))
            .kind(ResearchItemKind.WEB)
            .build());
      }
    }

    // 超长视频（默认 >30 分钟）不进核验与资料池，复用视频解析时长配置
    sourceCandidates = HotNewsResearchPoolQuotaSupport.rejectOverlongVideos(
        sourceCandidates, videoTranscriptService::durationLimitRejectReason);
    webCollected = HotNewsResearchPoolQuotaSupport.rejectOverlongVideos(
        webCollected, videoTranscriptService::durationLimitRejectReason);

    // AI 核验：同词不同义（如 Apple 水果 vs 苹果公司）一律剔除
    if (!webCollected.isEmpty()) {
      webCollected = researchAssist.filterRelevant(topicTitle, summary, keywordPlan, webCollected);
    }
    // 非中文标题/摘要译为简体中文（失败保留原文）
    if (!webCollected.isEmpty()) {
      webCollected = researchAssist.translateNonChinese(webCollected);
    }

    if (webCollected.isEmpty()) {
      log.warn("资料补充未获得新网页结果 topicId={} subject={} channels={} keptSources={}",
          resolvedTopicId,
          keywordPlan.getSubject(),
          searchChannels.stream().map(SourceChannelEntity::getName).toList(),
          sourceCandidates.size());
    }

    // 原始来源在前，网页补充在后；再裁到文章/视频各上限
    List<ResearchCandidate> collected = new ArrayList<>(sourceCandidates.size() + webCollected.size());
    collected.addAll(sourceCandidates);
    collected.addAll(webCollected);
    // 先裁配额再打分：applyLimits 不依赖分数，可减少无效 AI 调用
    collected = HotNewsResearchPoolQuotaSupport.applyLimits(collected, null);
    // 配额阶段已探测时长（rejectOverlongVideos），落库前写入候选便于列表展示
    collector.stampVideoDurations(collected);
    // 五维 AI 打分（失败回退 kind 默认分）
    if (!collected.isEmpty()) {
      researchItemScoreService.scoreCandidates(topicTitle, summary, collected);
    }
    HotNewsTopicResearchPoolTx.ReplaceResult replaced = researchPoolTx.replacePool(
        resolvedTopicId,
        run,
        collected,
        webCollected.isEmpty(),
        !sourceCandidates.isEmpty(),
        now);
    int itemCount = replaced.itemCount();
    int conflictCount = replaced.conflictCount();
    String status = replaced.status();

    String briefId;
    List<TopicBriefEntity> briefs = briefMapper.selectList(new LambdaQueryWrapper<TopicBriefEntity>()
        .eq(TopicBriefEntity::getTopicId, resolvedTopicId)
        .orderByDesc(TopicBriefEntity::getUpdatedAt)
        .last("LIMIT 1"));
    if (briefs.isEmpty()) {
      briefId = topics.createBrief(resolvedTopicId).getId();
    } else {
      briefId = briefs.get(0).getId();
    }

    return HotNewsResearchCompleteView.builder()
        .researchRunId(run.getId())
        .topicId(resolvedTopicId)
        .itemCount(itemCount)
        .conflictCount(conflictCount)
        .status(status)
        .briefId(briefId)
        .build();
  }


}
