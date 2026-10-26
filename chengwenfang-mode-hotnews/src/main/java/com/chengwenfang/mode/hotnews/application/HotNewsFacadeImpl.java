package com.chengwenfang.mode.hotnews.application;

import com.chengwenfang.sourcing.application.HotNewsChannelService;

import com.chengwenfang.common.PageQuery;
import com.chengwenfang.crawler.facade.dto.view.FetchedItemView;
import com.chengwenfang.mode.hotnews.facade.HotNewsFacade;
import com.chengwenfang.mode.hotnews.facade.dto.request.AiThemeSeedPersistRequest;
import com.chengwenfang.mode.hotnews.facade.dto.request.HotNewsArticleContentUpdateRequest;
import com.chengwenfang.mode.hotnews.facade.dto.request.HotNewsArticleUpdateRequest;
import com.chengwenfang.sourcing.facade.dto.request.HotNewsChannelCreateRequest;
import com.chengwenfang.sourcing.facade.dto.request.HotNewsChannelPatchRequest;
import com.chengwenfang.sourcing.facade.dto.request.HotNewsChannelUpsertRequest;
import com.chengwenfang.mode.hotnews.facade.dto.request.HotNewsGenerationRunRequest;
import com.chengwenfang.mode.hotnews.facade.dto.request.HotNewsGenerationSettingsRequest;
import com.chengwenfang.mode.hotnews.facade.dto.request.HotNewsRewriteRequest;
import com.chengwenfang.mode.hotnews.facade.dto.request.ManualResearchItemRequest;
import com.chengwenfang.sourcing.facade.dto.request.QuerySiteBlockUpsertRequest;
import com.chengwenfang.mode.hotnews.facade.dto.request.RadarSurvivorPersistRequest;
import com.chengwenfang.mode.hotnews.facade.dto.request.VideoParseRequest;
import com.chengwenfang.sourcing.facade.dto.view.ChannelView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsArticleSkeletonView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsArticleVersionView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsArticleView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsBriefView;
import com.chengwenfang.sourcing.facade.dto.view.HotNewsChannelDeleteView;
import com.chengwenfang.sourcing.facade.dto.view.HotNewsChannelPageView;
import com.chengwenfang.sourcing.facade.dto.view.HotNewsChannelRunView;
import com.chengwenfang.sourcing.facade.dto.view.HotNewsChannelTestView;
import com.chengwenfang.sourcing.facade.dto.view.HotNewsChannelView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsEvidenceView;
import com.chengwenfang.sourcing.facade.dto.view.HotNewsFetchRunView;
import com.chengwenfang.sourcing.facade.dto.view.HotNewsItemView;
import com.chengwenfang.sourcing.facade.dto.view.HotNewsPersistResult;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsResearchCompleteView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsResearchConflictView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsResearchItemPageView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsResearchItemView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsResearchRunView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsRewriteResultView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsTopicPageView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsTopicSourceView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsTopicView;
import com.chengwenfang.sourcing.facade.dto.view.QuerySiteBlockView;
import com.chengwenfang.mode.hotnews.facade.dto.view.RadarPersistClusterResult;
import com.chengwenfang.job.facade.JobExecutionContext;
import com.chengwenfang.job.facade.dto.view.JobView;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link HotNewsFacade} 薄门面：按职责转发到领域协作服务。
 *
 * <ul>
 *   <li>{@link HotNewsChannelService} — 信源频道</li>
 *   <li>{@link HotNewsFetchService} — 抓取运行与条目落库</li>
 *   <li>{@link HotNewsRadarCollectService} — 雷达批次与拉黑</li>
 *   <li>{@link HotNewsTopicService} — 选题池（聚合/简报/提炼薄委托）</li>
 *   <li>{@link HotNewsResearchService} — 调研（视频解析委托 {@link HotNewsVideoParseService}）</li>
 *   <li>{@link HotNewsArticleService} — 骨架/成文入队；文档轨委托 {@link HotNewsArticleDocumentService}</li>
 * </ul>
 */
@Slf4j
@Service
public class HotNewsFacadeImpl implements HotNewsFacade {

  private final HotNewsChannelService channels;
  private final HotNewsFetchService fetch;
  private final HotNewsRadarCollectService radar;
  private final HotNewsTopicService topics;
  private final HotNewsResearchService research;
  private final HotNewsArticleService articles;

  /**
   * @param channels 频道协作服务
   * @param fetch    抓取协作服务
   * @param radar    雷达协作服务
   * @param topics   选题协作服务
   * @param research 调研协作服务
   * @param articles 成文协作服务
   */
  public HotNewsFacadeImpl(
      HotNewsChannelService channels,
      HotNewsFetchService fetch,
      HotNewsRadarCollectService radar,
      HotNewsTopicService topics,
      HotNewsResearchService research,
      HotNewsArticleService articles) {
    this.channels = channels;
    this.fetch = fetch;
    this.radar = radar;
    this.topics = topics;
    this.research = research;
    this.articles = articles;
  }

  @Override
  public List<HotNewsChannelView> listChannels(String themeId) {
    return channels.listChannels(themeId);
  }

  @Override
  public HotNewsChannelPageView pageChannels(
      String themeId, PageQuery query, String type, Boolean enabled) {
    return channels.pageChannels(themeId, query, type, enabled);
  }

  @Override
  public HotNewsChannelView upsertChannel(HotNewsChannelUpsertRequest body) {
    return channels.upsertChannel(body);
  }

  @Override
  public HotNewsChannelView createChannel(String themeId, HotNewsChannelCreateRequest body) {
    return channels.createChannel(themeId, body);
  }

  @Override
  public HotNewsChannelView patchChannel(String channelId, HotNewsChannelPatchRequest body) {
    return channels.patchChannel(channelId, body);
  }

  @Override
  public HotNewsChannelDeleteView deleteChannel(String channelId) {
    return channels.deleteChannel(channelId);
  }

  @Override
  public HotNewsChannelTestView testChannel(String channelId) {
    return channels.testChannel(channelId);
  }

  @Override
  public List<ChannelView> loadChannels(String themeId, List<String> channelIds) {
    return channels.loadChannels(themeId, channelIds);
  }

  @Override
  public boolean hasActiveJobsForTheme(String themeId) {
    return fetch.hasActiveJobsForTheme(themeId);
  }

  @Override
  @Transactional
  public JobView startFetch(String themeId, List<String> channelIds, int hours) {
    return fetch.startFetch(themeId, channelIds, hours);
  }

  @Override
  @Transactional
  public JobView startFetch(
      String themeId, List<String> channelIds, int hours, Object jobInput) {
    return fetch.startFetch(themeId, channelIds, hours, jobInput);
  }

  @Override
  public List<HotNewsFetchRunView> listFetchRuns(String themeId) {
    return fetch.listFetchRuns(themeId);
  }

  @Override
  public HotNewsFetchRunView getFetchRun(String runId) {
    return fetch.getFetchRun(runId);
  }

  @Override
  public List<HotNewsItemView> listItems(String themeId, int limit) {
    return fetch.listItems(themeId, limit);
  }

  @Override
  public HotNewsPersistResult persistFetchedItems(
      String themeId, String channelId, List<FetchedItemView> items) {
    return fetch.persistFetchedItems(themeId, channelId, items);
  }

  @Override
  public HotNewsTopicView persistAiThemeSeedTopic(
      String themeId, String radarBatchId, AiThemeSeedPersistRequest request) {
    return topics.persistAiThemeSeedTopic(themeId, radarBatchId, request);
  }

  @Override
  public void finishRun(
      String runId,
      int totalItems,
      int newItems,
      int dedupedItems,
      List<HotNewsChannelRunView> channelRuns,
      String error) {
    fetch.finishRun(runId, totalItems, newItems, dedupedItems, channelRuns, error);
  }

  @Override
  public List<HotNewsTopicView> listTopics(String themeId, int limit) {
    return topics.listTopics(themeId, limit);
  }

  @Override
  @Transactional
  public List<HotNewsTopicView> listTopics(String themeId, String status, int limit) {
    return topics.listTopics(themeId, status, limit);
  }

  @Override
  @Transactional
  public HotNewsTopicPageView pageTopics(
      String themeId, PageQuery query, String filter, String sortBy) {
    return topics.pageTopics(themeId, query, filter, sortBy);
  }

  @Override
  public List<HotNewsTopicView> listConfirmedTopics(String themeId) {
    return topics.listConfirmedTopics(themeId);
  }

  @Override
  public Set<String> listConfirmedTopicMaterialDedupeKeys(String themeId) {
    return topics.listConfirmedTopicMaterialDedupeKeys(themeId);
  }

  @Override
  public HotNewsTopicView getTopic(String topicId) {
    return topics.getTopic(topicId);
  }

  @Override
  @Transactional
  public HotNewsTopicView setTopicStatus(String topicId, String status) {
    return topics.setTopicStatus(topicId, status);
  }

  @Override
  public List<HotNewsTopicSourceView> listTopicSources(String topicId) {
    return topics.listTopicSources(topicId);
  }

  @Override
  public List<HotNewsEvidenceView> listEvidence(String topicId) {
    return topics.listEvidence(topicId);
  }

  @Override
  public List<HotNewsBriefView> listBriefs(String topicId) {
    return topics.listBriefs(topicId);
  }

  @Override
  @Transactional
  public HotNewsBriefView createBrief(String topicId) {
    return topics.createBrief(topicId);
  }

  @Override
  @Transactional
  public HotNewsBriefView confirmBrief(String briefId, String note) {
    return topics.confirmBrief(briefId, note);
  }

  @Override
  @Transactional
  public int aggregateTopics(String themeId) {
    return topics.aggregateTopics(themeId);
  }

  @Override
  public int editorializeTopics(String themeId) {
    return topics.editorializeTopics(themeId);
  }

  @Override
  public HotNewsArticleSkeletonView getArticleSkeleton(String topicId) {
    return articles.getArticleSkeleton(topicId);
  }

  @Override
  @Transactional
  public HotNewsArticleSkeletonView buildArticleSkeleton(String topicId) {
    return articles.buildArticleSkeleton(topicId);
  }

  @Override
  @Transactional
  public HotNewsArticleSkeletonView buildArticleSkeleton(String topicId, String themeContext) {
    return articles.buildArticleSkeleton(topicId, themeContext);
  }

  @Override
  @Transactional
  public JobView startTopicResearch(String topicId) {
    return research.startTopicResearch(topicId);
  }

  @Override
  @Transactional
  public JobView startVideoParse(String topicId, VideoParseRequest request) {
    return research.startVideoParse(topicId, request);
  }

  @Override
  public void executeVideoParseJob(String jobId, String inputJson, JobExecutionContext context) {
    research.executeVideoParseJob(jobId, inputJson, context);
  }

  @Override
  @Transactional
  public JobView enqueuePendingSourceVideoParse(String themeId) {
    return research.enqueuePendingSourceVideoParse(themeId);
  }

  @Override
  public void executeSourceVideoParseJob(String jobId, String inputJson, JobExecutionContext context) {
    research.executeSourceVideoParseJob(jobId, inputJson, context);
  }

  @Override
  public List<HotNewsResearchRunView> listResearchRuns(String topicId) {
    return research.listResearchRuns(topicId);
  }

  @Override
  public HotNewsResearchRunView getResearchRun(String runId) {
    return research.getResearchRun(runId);
  }

  @Override
  public List<HotNewsResearchItemView> listResearchItems(String topicId) {
    return research.listResearchItems(topicId);
  }

  @Override
  public HotNewsResearchItemPageView pageResearchItems(String topicId, PageQuery query, String media) {
    return research.pageResearchItems(topicId, query, media);
  }

  @Override
  @Transactional
  public HotNewsResearchItemView addManualResearchItem(String topicId, ManualResearchItemRequest request) {
    return research.addManualResearchItem(topicId, request);
  }

  @Override
  public List<HotNewsResearchItemView> listArticleReferenceItems(String topicId) {
    return research.listArticleReferenceItems(topicId);
  }

  @Override
  public List<HotNewsResearchConflictView> listResearchConflicts(String topicId) {
    return research.listResearchConflicts(topicId);
  }

  @Override
  public HotNewsResearchCompleteView completeTopicResearch(String researchRunId, String topicId) {
    return research.completeTopicResearch(researchRunId, topicId);
  }

  @Override
  @Transactional
  public JobView generateArticleFromBrief(String briefId) {
    return articles.generateArticleFromBrief(briefId);
  }

  @Override
  @Transactional
  public JobView generateArticleFromTopic(String topicId) {
    return articles.generateArticleFromTopic(topicId);
  }

  @Override
  @Transactional
  public HotNewsArticleView ensureArticleForTopic(String topicId) {
    return articles.ensureArticleForTopic(topicId);
  }

  @Override
  public List<HotNewsArticleView> listArticlesByTopic(String topicId) {
    return articles.listArticlesByTopic(topicId);
  }

  @Override
  public HotNewsArticleView getHotArticle(String articleId) {
    return articles.getHotArticle(articleId);
  }

  @Override
  @Transactional
  public HotNewsArticleView updateHotArticle(String articleId, HotNewsArticleUpdateRequest body) {
    return articles.updateHotArticle(articleId, body);
  }

  @Override
  @Transactional
  public HotNewsArticleView updateArticleContent(
      String articleId, String kind, HotNewsArticleContentUpdateRequest body) {
    return articles.updateArticleContent(articleId, kind, body);
  }

  @Override
  @Transactional
  public HotNewsArticleView updateGenerationSettings(
      String articleId, HotNewsGenerationSettingsRequest body) {
    return articles.updateGenerationSettings(articleId, body);
  }

  @Override
  @Transactional
  public JobView startGenerationRun(String articleId, HotNewsGenerationRunRequest body) {
    return articles.startGenerationRun(articleId, body);
  }

  @Override
  @Transactional
  public JobView startGenerationRun(String articleId, Object input) {
    return articles.startGenerationRun(articleId, input);
  }

  @Override
  public List<JobView> listGenerationRuns(String articleId) {
    return articles.listGenerationRuns(articleId);
  }

  @Override
  public List<HotNewsArticleVersionView> listArticleVersions(String articleId) {
    return articles.listArticleVersions(articleId);
  }

  @Override
  public List<HotNewsArticleVersionView> listArticleVersions(String articleId, String documentId) {
    return articles.listArticleVersions(articleId, documentId);
  }

  @Override
  @Transactional
  public HotNewsArticleView commitHotNewsArticle(String articleId, String draft, String wechat) {
    return articles.commitHotNewsArticle(articleId, draft, wechat);
  }

  @Override
  @Transactional
  public JobView startArticleRewrite(String articleId, HotNewsRewriteRequest body) {
    return articles.startArticleRewrite(articleId, body);
  }

  @Override
  @Transactional
  public HotNewsArticleView applyAiRewrite(
      String articleId, String rewrittenDraft, String instruction) {
    return articles.applyAiRewrite(articleId, rewrittenDraft, instruction);
  }

  @Override
  @Transactional
  public HotNewsRewriteResultView createRewriteCandidate(String articleId, String instruction) {
    return articles.createRewriteCandidate(articleId, instruction);
  }

  @Override
  @Transactional
  public void ensureRadarBatch(String batchId, String themeId, String jobId) {
    radar.ensureRadarBatch(batchId, themeId, jobId);
  }

  @Override
  @Transactional
  public void completeRadarBatch(String batchId) {
    radar.completeRadarBatch(batchId);
  }

  @Override
  @Transactional
  public void failRadarBatch(String batchId) {
    radar.failRadarBatch(batchId);
  }

  @Override
  @Transactional
  public RadarPersistClusterResult persistAndClusterRadarSurvivors(
      String themeId, String radarBatchId, List<RadarSurvivorPersistRequest> survivors) {
    return radar.persistAndClusterRadarSurvivors(themeId, radarBatchId, survivors);
  }

  @Override
  @Transactional
  public void updateRadarClusterSummaries(Map<String, String> summaryByTopicId) {
    radar.updateRadarClusterSummaries(summaryByTopicId);
  }

  @Override
  public List<HotNewsTopicView> listRadarBatchTopics(String themeId, String radarBatchId) {
    return radar.listRadarBatchClusters(themeId, radarBatchId).stream()
        .map(topics::topicView)
        .toList();
  }

  @Override
  @Transactional
  public int applyWritingScores(
      List<com.chengwenfang.mode.hotnews.facade.dto.request.TopicWritingScoreApplyRequest> requests) {
    return radar.applyWritingScores(requests);
  }

  @Override
  @Transactional
  public int markRadarTop10(String themeId, String radarBatchId) {
    int marked = radar.markRadarTop10Ranks(themeId, radarBatchId);
    topics.applyDailyTopicLimit(themeId);
    return marked;
  }

  @Override
  public List<JobView> listRadarCollectRuns(String themeId) {
    return radar.listRadarCollectRuns(themeId);
  }

  @Override
  public List<QuerySiteBlockView> listActiveQuerySiteBlocks(String themeId) {
    return radar.listActiveQuerySiteBlocks(themeId);
  }

  @Override
  public List<QuerySiteBlockView> upsertQuerySiteBlocks(List<QuerySiteBlockUpsertRequest> requests) {
    return radar.upsertQuerySiteBlocks(requests);
  }
}
