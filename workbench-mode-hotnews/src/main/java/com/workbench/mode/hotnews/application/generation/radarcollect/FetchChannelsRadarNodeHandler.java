package com.workbench.mode.hotnews.application.generation.radarcollect;

import cn.hutool.core.util.StrUtil;
import com.workbench.common.IdsUtil;
import com.workbench.common.ThemeTitleRelevance;
import com.workbench.crawler.facade.ArticleContentFacade;
import com.workbench.crawler.facade.FetchFacade;
import com.workbench.crawler.facade.MarketingPageFilter;
import com.workbench.crawler.facade.constant.ChannelFetchDefaults;
import com.workbench.crawler.facade.dto.request.FetchRequest;
import com.workbench.crawler.facade.dto.view.FetchResult;
import com.workbench.crawler.facade.dto.view.FetchedItemView;
import com.workbench.crawler.facade.dto.view.UrlReachabilityView;
import com.workbench.mode.hotnews.domain.ResearchContentClassifier;
import com.workbench.mode.hotnews.domain.TopicResearchPlanner;
import com.workbench.mode.hotnews.facade.HotNewsFacade;
import com.workbench.mode.hotnews.facade.VideoTranscriptFacade;
import com.workbench.sourcing.facade.dto.view.ChannelView;
import com.workbench.sourcing.facade.dto.view.QuerySiteBlockView;
import com.workbench.sourcing.facade.enums.SourceMediaType;
import com.workbench.infra.config.WorkbenchProperties;
import com.workbench.job.facade.JobFacade;
import com.workbench.skill.facade.dto.view.SkillPromptBindingView;
import com.workbench.skill.facade.enums.HotNewsRadarCollectNodeType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** 并行渠道拉取到内存候选（本阶段不落库）。 */
@Slf4j
@Component
public class FetchChannelsRadarNodeHandler implements RadarCollectNodeHandler {
  private final HotNewsFacade hotNews;
  private final FetchFacade fetchFacade;
  private final Executor channelFetchExecutor;
  private final JobFacade jobs;
  private final WorkbenchProperties properties;
  private final SearchQueryDiagnoseService searchQueryDiagnose;
  /** 视频时长探测（配额裁剪前过滤超长视频）。 */
  private final VideoTranscriptFacade videoTranscript;
  /** 文章正文门面（含 URL 直连探测）。 */
  private final ArticleContentFacade articleContent;

  /**
   * @param hotNews              热点门面
   * @param fetchFacade          抓取门面
   * @param channelFetchExecutor 渠道并行执行器
   * @param jobs                 任务门面
   * @param properties           工作台配置
   * @param searchQueryDiagnose  搜索词×网站诊断
   * @param videoTranscript      视频解析门面（时长探测）
   * @param articleContent       文章正文门面（直连探测）
   */
  public FetchChannelsRadarNodeHandler(
      HotNewsFacade hotNews,
      FetchFacade fetchFacade,
      @Qualifier("channelFetchExecutor") Executor channelFetchExecutor,
      JobFacade jobs,
      WorkbenchProperties properties,
      SearchQueryDiagnoseService searchQueryDiagnose,
      VideoTranscriptFacade videoTranscript,
      ArticleContentFacade articleContent) {
    this.hotNews = hotNews;
    this.fetchFacade = fetchFacade;
    this.channelFetchExecutor = channelFetchExecutor;
    this.jobs = jobs;
    this.properties = properties;
    this.searchQueryDiagnose = searchQueryDiagnose;
    this.videoTranscript = videoTranscript;
    this.articleContent = articleContent;
  }

  @Override
  public HotNewsRadarCollectNodeType supports() {
    return HotNewsRadarCollectNodeType.FETCH_CHANNELS;
  }

  @Override
  public void execute(
      RadarCollectSessionStore.Session session,
      Map<String, Object> lightState,
      List<SkillPromptBindingView> skillBindings,
      String nodeKey,
      String nodeName) {
    List<ChannelView> channels = hotNews.loadChannels(session.getThemeId(), session.getChannelIds());
    String jobId = StrUtil.blankToDefault(
        stringVal(lightState.get(RadarCollectGraphStateKeys.JOB_ID)),
        session.getFetchRunId());
    String stepKey = HotNewsRadarCollectNodeType.FETCH_CHANNELS.getValue();
    int channelTotal = channels.size();
    // 加载主题级拉黑，供各渠道过滤已判定噪声组合
    Set<String> blockKeys = searchQueryDiagnose.loadActiveBlockKeys(session.getThemeId());
    reportProgress(
        jobId,
        stepKey,
        12,
        channelTotal == 0
            ? "未找到可采集渠道"
            : "正在并行采集 " + channelTotal + " 个渠道…");

    List<CompletableFuture<ChannelFetchOutcome>> futures = new ArrayList<>();
    for (ChannelView channel : channels) {
      futures.add(CompletableFuture.supplyAsync(
          () -> fetchChannel(session, channel, blockKeys),
          channelFetchExecutor));
    }

    Set<String> seenUrls = new LinkedHashSet<>();
    session.getCandidates().clear();
    AtomicInteger finished = new AtomicInteger();
    AtomicInteger failed = new AtomicInteger();
    AtomicInteger blockSkipped = new AtomicInteger();
    AtomicInteger rawFetched = new AtomicInteger();
    AtomicInteger rawArticleFetched = new AtomicInteger();
    AtomicInteger rawVideoFetched = new AtomicInteger();
    Map<String, SearchQueryDiagnoseService.MutableBucket> diagnoseBuckets =
        SearchQueryDiagnoseService.newBucketMap();
    for (CompletableFuture<ChannelFetchOutcome> future : futures) {
      RadarCollectLeaseSupport.renew(lightState);
      ChannelFetchOutcome outcome = future.join();
      int done = finished.incrementAndGet();
      if (!outcome.ok()) {
        failed.incrementAndGet();
      }
      blockSkipped.addAndGet(outcome.blockSkipped());
      rawFetched.addAndGet(outcome.rawCount());
      rawArticleFetched.addAndGet(outcome.rawArticleCount());
      rawVideoFetched.addAndGet(outcome.rawVideoCount());
      // 合并各渠道偏题桶，供本轮末尾诊断
      SearchQueryDiagnoseService.mergeBuckets(diagnoseBuckets, outcome.buckets());
      for (RadarCollectCandidate candidate : outcome.candidates()) {
        String key = StrUtil.blankToDefault(candidate.getUrl(), candidate.getTitle()).toLowerCase();
        if (StrUtil.isBlank(key) || !seenUrls.add(key)) {
          continue;
        }
        session.getCandidates().add(candidate);
      }
      String channelLabel = StrUtil.blankToDefault(outcome.channelName(), "未命名渠道");
      String detail = outcome.ok()
          ? ("收录 " + outcome.accepted() + " 条")
          : ("失败：" + StrUtil.blankToDefault(outcome.errorCode(), "unknown"));
      reportProgress(
          jobId,
          stepKey,
          progressForChannel(done, channelTotal),
          "渠道 "
              + done
              + "/"
              + channelTotal
              + "「"
              + channelLabel
              + "」"
              + detail
              + " · 累计候选 "
              + session.getCandidates().size()
              + " 条");
      if (StrUtil.isNotBlank(jobId)) {
        jobs.renewLease(jobId, properties.getWorkerLeaseSeconds());
      }
    }

    // 诊断失败不拖垮获取：捕获后仅记日志
    List<QuerySiteBlockView> newBlocks = List.of();
    try {
      newBlocks = searchQueryDiagnose.diagnoseAndUpsert(
          session.getThemeId(), SearchQueryDiagnoseService.freeze(diagnoseBuckets));
    } catch (Exception exception) {
      log.warn("搜索词诊断失败 themeId={} err={}", session.getThemeId(), exception.getMessage());
    }

    // 对照已选题素材剔除重复，避免占用直连探测与配额
    reportProgress(jobId, stepKey, 32, "正在剔除已选题重复素材…");
    Set<String> selectedMaterialKeys =
        hotNews.listConfirmedTopicMaterialDedupeKeys(session.getThemeId());
    int selectedMaterialDiscarded =
        discardAlreadySelectedMaterials(session, selectedMaterialKeys);

    // 先直连探测过滤不可打开 URL，再按时长/配额裁剪到文章 50、视频 10
    reportProgress(jobId, stepKey, 33, "正在探测候选链接是否可直接打开…");
    int unreachableDiscarded = discardUnreachableUrls(session, lightState, jobId);
    RadarCollectMediaQuotaSupport.CapResult quota =
        RadarCollectMediaQuotaSupport.applyCap(session, videoTranscript::durationLimitRejectReason);
    int passedArticle = 0;
    int passedVideo = 0;
    for (RadarCollectCandidate candidate : session.survivors()) {
      if (SourceMediaType.from(candidate.getMediaType()).isVideo()) {
        passedVideo++;
      } else {
        passedArticle++;
      }
    }
    int passed = passedArticle + passedVideo;
    int rawTotal = rawFetched.get();
    int rawArticle = rawArticleFetched.get();
    int rawVideo = rawVideoFetched.get();
    int filteredArticle = Math.max(0, rawArticle - passedArticle);
    int filteredVideo = Math.max(0, rawVideo - passedVideo);
    int filtered = filteredArticle + filteredVideo;
    String diagnoseHint = SearchQueryDiagnoseService.progressMessage(newBlocks);
    String summary = "获取完成：共获取 "
        + rawTotal
        + " 条（文章 "
        + rawArticle
        + "，视频 "
        + rawVideo
        + "），过滤掉 "
        + filtered
        + " 条（文章 "
        + filteredArticle
        + "，视频 "
        + filteredVideo
        + "，其中已选题去重 "
        + selectedMaterialDiscarded
        + "、直连不可达 "
        + unreachableDiscarded
        + "），传递下一步 "
        + passed
        + " 条（文章 "
        + passedArticle
        + "，视频 "
        + passedVideo
        + "）";
    // 拉黑跳过 / 搜索词诊断 / 配额裁剪细节仅记日志，不进执行页进度小字
    if (blockSkipped.get() > 0
        || StrUtil.isNotBlank(diagnoseHint)
        || quota.total() > 0
        || unreachableDiscarded > 0
        || selectedMaterialDiscarded > 0) {
      log.info(
          "雷达获取拉黑/已选题/直连/配额统计 themeId={} blockSkipped={} selectedMaterialDiscarded={} unreachable={} diagnose={} articleQuotaDiscarded={} videoQuotaDiscarded={}",
          session.getThemeId(),
          blockSkipped.get(),
          selectedMaterialDiscarded,
          unreachableDiscarded,
          StrUtil.blankToDefault(diagnoseHint, "-"),
          quota.articleDiscarded(),
          quota.videoDiscarded());
    }
    lightState.put(RadarCollectGraphStateKeys.CANDIDATE_COUNT, passed);
    lightState.put(RadarCollectGraphStateKeys.FETCH_RAW_COUNT, rawTotal);
    lightState.put(RadarCollectGraphStateKeys.FETCH_FILTERED_COUNT, filtered);
    lightState.put(RadarCollectGraphStateKeys.FETCH_NOTE, summary);
    reportProgress(jobId, stepKey, 35, summary);
    log.info(
        "雷达获取完成 themeId={} raw={} rawArticle={} rawVideo={} filtered={} filteredArticle={} filteredVideo={} selectedMaterialDiscarded={} unreachable={} passed={} article={} video={} channels={}/{} blockSkipped={} newBlocks={}",
        session.getThemeId(),
        rawTotal,
        rawArticle,
        rawVideo,
        filtered,
        filteredArticle,
        filteredVideo,
        selectedMaterialDiscarded,
        unreachableDiscarded,
        passed,
        passedArticle,
        passedVideo,
        channelTotal - failed.get(),
        channelTotal,
        blockSkipped.get(),
        newBlocks.size());
  }

  /**
   * 按已选题素材排除键弃用候选（不占后续直连探测与配额）。
   *
   * @param session        会话
   * @param exclusionKeys  已选题素材去重键
   * @return 因已选题重复弃用的条数
   */
  static int discardAlreadySelectedMaterials(
      RadarCollectSessionStore.Session session, Set<String> exclusionKeys) {
    if (session == null || exclusionKeys == null || exclusionKeys.isEmpty()) {
      return 0;
    }
    int discarded = 0;
    for (RadarCollectCandidate candidate : session.getCandidates()) {
      if (candidate == null || candidate.isDiscarded()) {
        continue;
      }
      String key = TopicResearchPlanner.materialDedupeKey(candidate.getUrl(), candidate.getTitle());
      if (!TopicResearchPlanner.isMeaningfulMaterialDedupeKey(key)
          || !exclusionKeys.contains(key)) {
        continue;
      }
      candidate.setDiscarded(true);
      candidate.setDiscardReason("已选题素材重复");
      candidate.setRawText(null);
      discarded++;
      log.info(
          "雷达候选已选题去重 title={} url={}",
          candidate.getTitle(),
          candidate.getUrl());
    }
    return discarded;
  }

  /**
   * 并行直连探测：不可打开的 URL 标记弃用（不占后续文章/视频配额）。
   *
   * @param session    会话
   * @param lightState 轻量状态（续租）
   * @param jobId      任务 ID
   * @return 因不可达弃用的条数
   */
  private int discardUnreachableUrls(
      RadarCollectSessionStore.Session session,
      Map<String, Object> lightState,
      String jobId) {
    List<RadarCollectCandidate> pending = new ArrayList<>();
    for (RadarCollectCandidate candidate : session.getCandidates()) {
      if (candidate != null && !candidate.isDiscarded() && StrUtil.isNotBlank(candidate.getUrl())) {
        pending.add(candidate);
      }
    }
    if (pending.isEmpty()) {
      return 0;
    }
    AtomicInteger unreachable = new AtomicInteger();
    AtomicInteger done = new AtomicInteger();
    List<CompletableFuture<Void>> futures = new ArrayList<>(pending.size());
    for (RadarCollectCandidate candidate : pending) {
      futures.add(CompletableFuture.runAsync(() -> {
        UrlReachabilityView probe = articleContent.probeUrlReachability(candidate.getUrl());
        if (probe == null || !probe.isReachable()) {
          String reason = probe == null ? "不可达" : StrUtil.blankToDefault(probe.getReason(), "不可达");
          candidate.setDiscarded(true);
          candidate.setDiscardReason("直连不可打开：" + reason);
          candidate.setRawText(null);
          unreachable.incrementAndGet();
          log.info(
              "雷达候选直连不可达 title={} reason={} url={}",
              candidate.getTitle(),
              reason,
              candidate.getUrl());
        }
        int finishedCount = done.incrementAndGet();
        if (finishedCount == pending.size() || finishedCount % 20 == 0) {
          reportProgress(
              jobId,
              HotNewsRadarCollectNodeType.FETCH_CHANNELS.getValue(),
              33,
              "直连探测 "
                  + finishedCount
                  + "/"
                  + pending.size()
                  + "，不可达 "
                  + unreachable.get());
          RadarCollectLeaseSupport.renew(lightState);
          if (StrUtil.isNotBlank(jobId)) {
            try {
              jobs.renewLease(jobId, properties.getWorkerLeaseSeconds());
            } catch (Exception exception) {
              log.warn("直连探测租约续期失败 jobId={} err={}", jobId, exception.getMessage());
            }
          }
        }
      }, channelFetchExecutor));
    }
    CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
    return unreachable.get();
  }

  /**
   * 单渠道拉取、拉黑过滤、主题过滤与桶统计。
   *
   * @param session   会话
   * @param channel   渠道
   * @param blockKeys 生效拉黑键
   * @return 渠道结果
   */
  private ChannelFetchOutcome fetchChannel(
      RadarCollectSessionStore.Session session,
      ChannelView channel,
      Set<String> blockKeys) {
    String channelName = channel == null ? "" : StrUtil.nullToEmpty(channel.getName());
    String channelType = channel == null ? "" : StrUtil.nullToEmpty(channel.getType());
    FetchResult result = fetchFacade.fetch(FetchRequest.builder()
        .url(channel.getBaseUrl())
        .type(channel.getType())
        .timeoutSeconds(ChannelFetchDefaults.DEFAULT_TIMEOUT_SECONDS)
        .hours(session.getHours())
        .config(channel.getConfig())
        .build());
    if (!result.isOk() || result.getItems() == null) {
      log.warn("渠道采集失败 name={} code={}", channelName, result.getErrorCode());
      return new ChannelFetchOutcome(
          channelName, false, result.getErrorCode(), 0, 0, 0, 0, 0, List.of(), List.of());
    }
    List<RadarCollectCandidate> out = new ArrayList<>();
    Map<String, SearchQueryDiagnoseService.MutableBucket> buckets =
        SearchQueryDiagnoseService.newBucketMap();
    int accepted = 0;
    int blockSkipped = 0;
    int rawCount = 0;
    int rawArticleCount = 0;
    int rawVideoCount = 0;
    for (FetchedItemView item : result.getItems()) {
      if (item == null) {
        continue;
      }
      rawCount++;
      boolean video = ResearchContentClassifier.isVideo(item.getUrl(), item.getTitle());
      if (video) {
        rawVideoCount++;
      } else {
        rawArticleCount++;
      }
      if (MarketingPageFilter.shouldSkip(item.getUrl(), item.getTitle())) {
        log.info("标题硬广跳过 title={} url={}", item.getTitle(), item.getUrl());
        continue;
      }
      // 已拉黑组合只记跳过，不进入二次诊断桶
      if (searchQueryDiagnose.isBlocked(blockKeys, channelType, item.getSearchQuery(), item.getUrl())) {
        blockSkipped++;
        log.info(
            "拉黑跳过 query={} title={} url={}",
            item.getSearchQuery(),
            item.getTitle(),
            item.getUrl());
        continue;
      }
      ThemeTitleRelevance.Decision themeDecision = ThemeTitleRelevance.judge(
          session.getThemeRelevanceProfile(), item.getTitle(), item.getSummary());
      // 词典未命中先留下，素材筛选再整包问模型；只有空标题才在这里丢掉
      boolean themeSkipped = themeDecision.hardDrop();
      SearchQueryDiagnoseService.accumulate(channelType, item, themeSkipped, buckets);
      if (themeSkipped) {
        log.info(
            "标题主题不相关跳过 title={} reason={} url={}",
            item.getTitle(),
            themeDecision.reason(),
            item.getUrl());
        continue;
      }
      accepted++;
      out.add(RadarCollectCandidate.builder()
          .tempId(IdsUtil.newId())
          .themeId(session.getThemeId())
          .channelId(channel.getId())
          .channelName(channel.getName())
          .channelType(channel.getType())
          .url(item.getUrl())
          .title(item.getTitle())
          .author(item.getAuthor())
          .publishedAt(item.getPublishedAt())
          .fetchSummary(item.getSummary())
          .siteLabel(ResearchContentClassifier.siteLabel(item.getUrl()))
          .searchQuery(item.getSearchQuery())
          .mediaType(video ? SourceMediaType.VIDEO.getValue() : SourceMediaType.ARTICLE.getValue())
          .build());
    }
    return new ChannelFetchOutcome(
        channelName,
        true,
        null,
        rawCount,
        rawArticleCount,
        rawVideoCount,
        accepted,
        blockSkipped,
        out,
        SearchQueryDiagnoseService.freeze(buckets));
  }

  /**
   * 上报 Job 进度。
   *
   * @param jobId    任务 ID
   * @param stepKey  步骤键
   * @param progress 进度
   * @param message  文案
   */
  private void reportProgress(String jobId, String stepKey, int progress, String message) {
    if (StrUtil.isBlank(jobId)) {
      return;
    }
    try {
      jobs.updateProgress(jobId, stepKey, Math.min(90, Math.max(1, progress)), message);
    } catch (Exception exception) {
      log.warn("雷达获取进度上报失败 jobId={} err={}", jobId, exception.getMessage());
    }
  }

  /**
   * 按渠道完成数映射进度区间 12–32。
   *
   * @param done  已完成渠道数
   * @param total 总渠道数
   * @return 进度值
   */
  private static int progressForChannel(int done, int total) {
    if (total <= 0) {
      return 20;
    }
    return 12 + Math.round(done * 20f / total);
  }

  /**
   * @param value 任意对象
   * @return 字符串或 null
   */
  private static String stringVal(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  /**
   * 单渠道拉取结果。
   */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  private static final class ChannelFetchOutcome {
    /** 渠道名。 */
    private final String channelName;
    /** 是否成功。 */
    private final boolean ok;
    /** 失败码。 */
    private final String errorCode;
    /** 上游返回非空条目数（过滤前）。 */
    private final int rawCount;
    /** 上游文章条数。 */
    private final int rawArticleCount;
    /** 上游视频条数。 */
    private final int rawVideoCount;
    /** 主题过滤后条数。 */
    private final int accepted;
    /** 拉黑跳过条数。 */
    private final int blockSkipped;
    /** 候选列表。 */
    private final List<RadarCollectCandidate> candidates;
    /** 本渠道偏题桶。 */
    private final List<SearchQueryDiagnoseService.BucketStat> buckets;
  }
}
