package com.chengwenfang.mode.hotnews.facade;

import com.chengwenfang.common.PageQuery;
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
import com.chengwenfang.mode.hotnews.facade.dto.request.AiThemeSeedPersistRequest;
import com.chengwenfang.mode.hotnews.facade.dto.request.RadarSurvivorPersistRequest;
import com.chengwenfang.mode.hotnews.facade.dto.request.VideoParseRequest;
import com.chengwenfang.job.facade.JobExecutionContext;
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
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsResearchCompleteView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsResearchConflictView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsResearchItemPageView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsResearchItemView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsResearchRunView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsRewriteResultView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsTopicPageView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsTopicSourceView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsTopicView;
import com.chengwenfang.sourcing.facade.dto.view.HotNewsPersistResult;
import com.chengwenfang.sourcing.facade.dto.view.QuerySiteBlockView;
import com.chengwenfang.mode.hotnews.facade.dto.view.RadarPersistClusterResult;
import com.chengwenfang.crawler.facade.dto.view.FetchedItemView;
import com.chengwenfang.job.facade.dto.view.JobView;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 热点资讯门面：信源频道、抓取、选题聚类、调研、简报与文章生成。
 */
public interface HotNewsFacade {
  /** 列出主题下的信源频道。 */
  List<HotNewsChannelView> listChannels(String themeId);

  /**
   * 分页列出主题下的信源频道（名称/地址关键字、类型、启用状态可筛选）。
   *
   * @param themeId 主题 ID
   * @param query   分页与关键字
   * @param type    频道类型；空则不过滤
   * @param enabled 启用状态；null 则不过滤
   */
  HotNewsChannelPageView pageChannels(String themeId, PageQuery query, String type, Boolean enabled);

  /** 按完整字段创建或覆盖更新频道。 */
  HotNewsChannelView upsertChannel(HotNewsChannelUpsertRequest body);

  /** 在指定主题下新建频道。 */
  HotNewsChannelView createChannel(String themeId, HotNewsChannelCreateRequest body);

  /** 局部更新频道字段。 */
  HotNewsChannelView patchChannel(String channelId, HotNewsChannelPatchRequest body);

  /** 软删除频道（禁用）。 */
  HotNewsChannelDeleteView deleteChannel(String channelId);

  /** 探测频道连通性与样例条目数。 */
  HotNewsChannelTestView testChannel(String channelId);

  /** 启动主题抓取任务。 */
  JobView startFetch(String themeId, List<String> channelIds, int hours);

  /**
   * 启动主题抓取任务（可附带 workflowId / skillSnapshot 等 Job input）。
   *
   * @param themeId    主题 ID
   * @param channelIds 渠道 ID
   * @param hours      回溯小时
   * @param jobInput   额外 Job 入参；可为 null
   * @return 已创建 Job
   */
  JobView startFetch(String themeId, List<String> channelIds, int hours, Object jobInput);

  /**
   * 主题下是否仍有进行中的抓取 / 研究 / 成文任务。
   *
   * @param themeId 主题主键
   * @return 存在进行中任务时为 true
   */
  boolean hasActiveJobsForTheme(String themeId);

  /** 列出抓取运行历史。 */
  List<HotNewsFetchRunView> listFetchRuns(String themeId);

  /**
   * 按主键读取抓取运行。
   *
   * @param runId 抓取运行 ID
   * @return 运行视图
   */
  HotNewsFetchRunView getFetchRun(String runId);

  /** 列出已抓取信源条目。 */
  List<HotNewsItemView> listItems(String themeId, int limit);

  /** 列出选题（默认排除 expired）。 */
  List<HotNewsTopicView> listTopics(String themeId, int limit);

  /** 按状态筛选选题列表。 */
  List<HotNewsTopicView> listTopics(String themeId, String status, int limit);

  /**
   * 分页列出热点雷达选题（筛选、关键字、排序在服务端完成）。
   *
   * @param themeId 主题 ID
   * @param query   分页与关键字
   * @param filter  筛选：today / all / candidate / confirmed
   * @param sortBy  排序：writing / heat
   * @return 当前页与雷达池级统计
   */
  HotNewsTopicPageView pageTopics(String themeId, PageQuery query, String filter, String sortBy);

  /**
   * 列出主题下全部已确认选题（含写作中、已完成），供已选题页。
   *
   * @param themeId 主题 ID
   */
  List<HotNewsTopicView> listConfirmedTopics(String themeId);

  /**
   * 汇总主题下已选题（confirmed/writing/completed）的全部素材去重键，供雷达采集排除重复。
   * <p>含资料池与选题关联信源；键规则见 {@code TopicResearchPlanner#materialDedupeKey}。
   *
   * @param themeId 主题 ID
   * @return 去重键集合；无已选题或无素材时为空集
   */
  Set<String> listConfirmedTopicMaterialDedupeKeys(String themeId);

  /** 获取单个选题详情。 */
  HotNewsTopicView getTopic(String topicId);

  /** 更新选题状态。 */
  HotNewsTopicView setTopicStatus(String topicId, String status);

  /** 列出选题关联的信源证据。 */
  List<HotNewsTopicSourceView> listTopicSources(String topicId);

  /** 列出选题证据；无结构化证据时回退为来源列表。 */
  List<HotNewsEvidenceView> listEvidence(String topicId);

  /** 列出选题简报。 */
  List<HotNewsBriefView> listBriefs(String topicId);

  /** 基于选题与来源创建草稿简报。 */
  HotNewsBriefView createBrief(String topicId);

  /** 确认简报（可附确认备注）。 */
  HotNewsBriefView confirmBrief(String briefId, String note);

  /** 读取选题文章骨架；无则返回 null。 */
  HotNewsArticleSkeletonView getArticleSkeleton(String topicId);

  /** 根据选题与资料池拟定文章骨架并落库。 */
  HotNewsArticleSkeletonView buildArticleSkeleton(String topicId);

  /**
   * 根据选题与资料池拟定文章骨架并落库。
   *
   * @param topicId      选题 ID
   * @param themeContext 主题读者/边界等成文上下文；可空
   */
  HotNewsArticleSkeletonView buildArticleSkeleton(String topicId, String themeContext);

  /** 启动选题深度调研任务。 */
  JobView startTopicResearch(String topicId);

  /**
   * 启动资料池视频解析任务（批量或指定条目）。
   *
   * @param topicId 选题 ID
   * @param request 条目与起始步骤；itemIds 空则解析全部未成功视频
   */
  JobView startVideoParse(String topicId, VideoParseRequest request);

  /**
   * 执行视频解析 Job（供 Worker 调用）。
   *
   * @param jobId     任务 ID
   * @param inputJson 任务输入 JSON
   * @param context   进度上下文
   */
  void executeVideoParseJob(String jobId, String inputJson, JobExecutionContext context);

  /** 列出调研运行。 */
  List<HotNewsResearchRunView> listResearchRuns(String topicId);

  /**
   * 按主键读取选题调研运行。
   *
   * @param runId 调研运行 ID
   * @return 运行视图
   */
  HotNewsResearchRunView getResearchRun(String runId);

  /** 列出调研材料条目。 */
  List<HotNewsResearchItemView> listResearchItems(String topicId);

  /**
   * 分页列出调研材料（标题/摘要/来源关键字、文章/视频可筛选）。
   *
   * @param topicId 选题 ID
   * @param query   分页与关键字
   * @param media   内容形态：all / article / video；空视为 all
   */
  HotNewsResearchItemPageView pageResearchItems(String topicId, PageQuery query, String media);

  /**
   * 手动添加一条资料：访问 URL 采集标题/摘要等，并打上与自动资料一致的站点/形态标签。
   *
   * @param topicId 选题 ID
   * @param request 含 url
   * @return 新条目视图
   */
  HotNewsResearchItemView addManualResearchItem(String topicId, ManualResearchItemRequest request);

  /**
   * 列出可用于文章骨架/成文的参考资料（视频须解析成功且有摘要）。
   */
  List<HotNewsResearchItemView> listArticleReferenceItems(String topicId);

  /** 列出调研冲突提示。 */
  List<HotNewsResearchConflictView> listResearchConflicts(String topicId);

  /** 由简报创建热点文章并入队生成（兼容旧入口）。 */
  JobView generateArticleFromBrief(String briefId);

  /** 由选题创建/复用热点文章并入队生成（按骨架+资料池参考）。 */
  JobView generateArticleFromTopic(String topicId);

  /**
   * 确保选题下存在文章壳（复用最新或新建），不入队生成。
   * 若有骨架推荐字数/插图，写入 generationSettings。
   */
  HotNewsArticleView ensureArticleForTopic(String topicId);

  /** 列出选题下的热点文章。 */
  List<HotNewsArticleView> listArticlesByTopic(String topicId);

  /** 获取热点文章详情。 */
  HotNewsArticleView getHotArticle(String articleId);

  /** 更新公众号正文（带 contentHash 冲突检测）。 */
  HotNewsArticleView updateHotArticle(String articleId, HotNewsArticleUpdateRequest body);

  /** 按 kind（draft/wechat）更新双轨内容并写版本。 */
  HotNewsArticleView updateArticleContent(String articleId, String kind, HotNewsArticleContentUpdateRequest body);

  /** 合并更新文章生成参数。 */
  HotNewsArticleView updateGenerationSettings(String articleId, HotNewsGenerationSettingsRequest body);

  /** 启动/重跑文章生成任务。 */
  JobView startGenerationRun(String articleId, HotNewsGenerationRunRequest body);

  /** 启动/重跑文章生成任务（可附带 workflowId 等扩展入参）。 */
  JobView startGenerationRun(String articleId, Object input);

  /** 列出文章生成任务运行。 */
  List<JobView> listGenerationRuns(String articleId);

  /** 列出热点文章版本历史。 */
  List<HotNewsArticleVersionView> listArticleVersions(String articleId);

  /**
   * 列出热点文章版本历史。
   *
   * @param articleId  文章主键
   * @param documentId 统一文档 id（通常为 wechat 轨）；可空
   */
  List<HotNewsArticleVersionView> listArticleVersions(String articleId, String documentId);

  /**
   * 完成一次选题调研：汇集来源、网页检索、去重入库、冲突检测，并确保存在简报。
   */
  HotNewsResearchCompleteView completeTopicResearch(String researchRunId, String topicId);

  /** 提交生成结果，写入原稿与公众号正文并标记 ready。 */
  HotNewsArticleView commitHotNewsArticle(String articleId, String draft, String wechat);

  /**
   * 入队热点文章 AI 改写任务。
   *
   * @param articleId 文章 ID
   * @param body      改写请求；可为 null
   * @return 已入队 Job
   */
  JobView startArticleRewrite(String articleId, HotNewsRewriteRequest body);

  /**
   * 落库 AI 改写结果（原稿 + 改写指令/时间）。
   *
   * @param articleId      文章 ID
   * @param rewrittenDraft 改写后原稿
   * @param instruction    改写指令
   * @return 文章视图
   */
  HotNewsArticleView applyAiRewrite(String articleId, String rewrittenDraft, String instruction);

  /** 基于指令生成改写候选草稿（本地拼接，非模型调用）。 */
  HotNewsRewriteResultView createRewriteCandidate(String articleId, String instruction);

  /** 加载主题下已启用频道（可按 ID 过滤），供抓取 Worker 使用。 */
  List<ChannelView> loadChannels(String themeId, List<String> channelIds);

  /** 将抓取结果入库，按频道 externalId 去重。 */
  HotNewsPersistResult persistFetchedItems(String themeId, String channelId, List<FetchedItemView> items);

  /**
   * 为待解析雷达视频入队自动解析任务（未启用视频解析则跳过）。
   *
   * @param themeId 主题 ID
   * @return 任务视图；无需解析时返回 null
   */
  JobView enqueuePendingSourceVideoParse(String themeId);

  /**
   * 执行雷达信源视频解析：失败弃用；成功则事实速览 + 营销判定后就绪或弃用，并增量挂接选题。
   *
   * @param jobId     任务 ID
   * @param inputJson 输入 JSON（themeId）
   * @param context   进度上下文
   */
  void executeSourceVideoParseJob(String jobId, String inputJson, JobExecutionContext context);

  /** 将近日信源聚合为选题并重算评分。 */
  int aggregateTopics(String themeId);

  /**
   * 将英文选题提炼为中文标题与事实速览。
   *
   * @param themeId 主题 ID
   * @return 成功写入速览的条数
   */
  int editorializeTopics(String themeId);

  /**
   * 确保雷达批次行存在（幂等插入）。
   *
   * @param batchId 批次 ID
   * @param themeId 主题 ID
   * @param jobId   触发 Job ID，可空
   */
  void ensureRadarBatch(String batchId, String themeId, String jobId);

  /**
   * 将雷达批次标为已完成。
   *
   * @param batchId 批次 ID
   */
  void completeRadarBatch(String batchId);

  /**
   * 将雷达批次标为失败。
   *
   * @param batchId 批次 ID
   */
  void failRadarBatch(String batchId);

  /**
   * 雷达幸存者落库并成簇，标记本轮批次；返回簇与成员速览供 AI 合并摘要。
   *
   * @param themeId      主题 ID
   * @param radarBatchId 本轮批次 ID
   * @param survivors    幸存者
   * @return 落库与成簇结果
   */
  RadarPersistClusterResult persistAndClusterRadarSurvivors(
      String themeId, String radarBatchId, List<RadarSurvivorPersistRequest> survivors);

  /**
   * 写入簇摘要（合并后的事实速览）。
   *
   * @param summaryByTopicId 选题 ID → 摘要
   */
  void updateRadarClusterSummaries(Map<String, String> summaryByTopicId);

  /**
   * 落库一条 AI 主题种子素材（不经渠道抓取）；同批次同标题幂等，已存在则返回已有选题。
   *
   * @param themeId      主题 ID
   * @param radarBatchId 雷达批次 ID
   * @param request      标题/速览/信号
   * @return 选题视图；参数无效或内容为空时 null
   */
  HotNewsTopicView persistAiThemeSeedTopic(
      String themeId, String radarBatchId, AiThemeSeedPersistRequest request);

  /**
   * 列出本雷达批次下未过期选题（供 AI 写作价值评分）。
   *
   * @param themeId      主题 ID
   * @param radarBatchId 批次 ID
   * @return 选题列表
   */
  List<HotNewsTopicView> listRadarBatchTopics(String themeId, String radarBatchId);

  /**
   * 写回 AI 写作价值评分，并按阈值提升 candidate。
   *
   * @param requests 评分结果
   * @return 成功写入条数
   */
  int applyWritingScores(List<com.chengwenfang.mode.hotnews.facade.dto.request.TopicWritingScoreApplyRequest> requests);

  /**
   * 对本批次选题按写作分排序，写入 radar_rank（1..10 为 Top10）。
   *
   * @param themeId      主题 ID
   * @param radarBatchId 批次 ID
   * @return Top10 数量
   */
  int markRadarTop10(String themeId, String radarBatchId);

  /**
   * 列出主题下雷达采集 Job（hot_news_fetch）。
   *
   * @param themeId 主题 ID
   * @return Job 列表
   */
  List<JobView> listRadarCollectRuns(String themeId);

  /**
   * 列出主题下生效中的搜索词 × 网站拉黑。
   *
   * @param themeId 主题 ID
   * @return 拉黑列表
   */
  List<QuerySiteBlockView> listActiveQuerySiteBlocks(String themeId);

  /**
   * 批量 upsert 搜索词 × 网站拉黑（强化 hit_count，状态置为 active）。
   *
   * @param requests upsert 请求
   * @return 写入后的视图
   */
  List<QuerySiteBlockView> upsertQuerySiteBlocks(List<QuerySiteBlockUpsertRequest> requests);

  /** 回写抓取运行终态与条目统计。 */
  void finishRun(
      String runId,
      int totalItems,
      int newItems,
      int dedupedItems,
      List<HotNewsChannelRunView> channelRuns,
      String error);
}
