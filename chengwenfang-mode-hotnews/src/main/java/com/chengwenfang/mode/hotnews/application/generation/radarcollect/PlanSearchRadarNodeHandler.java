package com.chengwenfang.mode.hotnews.application.generation.radarcollect;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.ai.facade.AiTextFacade;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.dto.request.TextCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.TextCompleteResult;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.common.LlmJsonSupport;
import com.chengwenfang.common.SearchQueryNormalizer;
import com.chengwenfang.content.facade.enums.HotNewsCollectStrategyMode;
import com.chengwenfang.crawler.facade.FetchFacade;
import com.chengwenfang.crawler.facade.MarketingPageFilter;
import com.chengwenfang.crawler.facade.constant.ChannelFetchDefaults;
import com.chengwenfang.crawler.facade.dto.request.ChannelFetchConfig;
import com.chengwenfang.crawler.facade.dto.request.FetchRequest;
import com.chengwenfang.crawler.facade.dto.view.FetchResult;
import com.chengwenfang.crawler.facade.dto.view.FetchedItemView;
import com.chengwenfang.crawler.facade.enums.ChannelType;
import com.chengwenfang.job.facade.JobFacade;
import com.chengwenfang.mode.hotnews.application.generation.radarcollect.model.AdaptiveSearchExtractAiResult;
import com.chengwenfang.mode.hotnews.application.generation.radarcollect.model.AdaptiveSearchPlanAiResult;
import com.chengwenfang.mode.hotnews.application.search.HotNewsSearchChannelSelector;
import com.chengwenfang.mode.hotnews.application.search.TavilySearchConfigAssembler;
import com.chengwenfang.mode.hotnews.application.search.TavilySearchContext;
import com.chengwenfang.mode.hotnews.domain.ResearchContentClassifier;
import com.chengwenfang.mode.hotnews.facade.HotNewsFacade;
import com.chengwenfang.mode.hotnews.facade.enums.TavilySearchScene;
import com.chengwenfang.skill.facade.dto.view.SkillPromptBindingView;
import com.chengwenfang.skill.facade.enums.HotNewsRadarCollectNodeType;
import com.chengwenfang.sourcing.facade.dto.view.ChannelView;
import com.chengwenfang.sourcing.facade.enums.SourceMediaType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 非结构化策略：按主要意图多轮规划搜索（优先 Tavily，否则 SearXNG）并追加候选；结构化策略跳过。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanSearchRadarNodeHandler implements RadarCollectNodeHandler {
  /** 最多搜索轮数。 */
  private static final int MAX_ROUNDS = 2;
  /** 每轮最多查询词。 */
  private static final int MAX_QUERIES_PER_ROUND = 6;
  /** 每轮每词最多条数。 */
  private static final int MAX_RESULTS_PER_QUERY = 10;
  /** 本节点最多追加候选。 */
  private static final int MAX_APPEND = 40;
  /** 历史占位 channelId；不可写入 source_items（无外键）。 */
  static final String LEGACY_ADAPTIVE_CHANNEL_PLACEHOLDER = "adaptive-searxng";

  private static final String PLAN_SYSTEM = """
      你是热点采集规划师。根据主要意图生成本轮元搜索短词（主体+动作/事件，禁止裸机构名）。
      只返回合法 JSON：{"goal":"本轮目标","queries":["词1","词2"]}
      queries 3～6 条，每条不超过 64 字。
      """;

  private static final String EXTRACT_SYSTEM = """
      你是热点线索抽取员。从搜索标题列表抽出可继续深挖的中间实体。
      只返回合法 JSON：{"sectors":["板块"],"tickers":["公司或股票简称"],"people":["人物"]}
      每类最多 5 条；没有则空数组。不要百科机构职能名。
      """;

  private static final String EXPAND_SYSTEM = """
      你是热点采集规划师。根据主要意图与已发现线索，生成下一轮深挖搜索词。
      只返回合法 JSON：{"goal":"本轮目标","queries":["词1","词2"]}
      示例：某板块 资金流向、某股票 回购、某人 最新观点。queries 3～6 条。
      """;

  private final FetchFacade fetchFacade;
  private final AiTextFacade ai;
  private final JobFacade jobs;
  /** 热点门面（解析主题真实搜索渠道）。 */
  private final HotNewsFacade hotNews;
  /** 联网渠道选择。 */
  private final HotNewsSearchChannelSelector searchChannelSelector;
  /** Tavily 配置装配。 */
  private final TavilySearchConfigAssembler tavilySearchConfigAssembler;

  @Override
  public HotNewsRadarCollectNodeType supports() {
    return HotNewsRadarCollectNodeType.PLAN_SEARCH;
  }

  @Override
  public void execute(
      RadarCollectSessionStore.Session session,
      Map<String, Object> lightState,
      List<SkillPromptBindingView> skillBindings,
      String nodeKey,
      String nodeName) {
    String jobId = StrUtil.blankToDefault(
        stringVal(lightState.get(RadarCollectGraphStateKeys.JOB_ID)),
        session.getFetchRunId());
    String stepKey = HotNewsRadarCollectNodeType.PLAN_SEARCH.getValue();
    if (!HotNewsCollectStrategyMode.isUnstructured(session.getCollectStrategyMode())) {
      String note = "结构化策略，跳过意图自适应搜索";
      lightState.put(RadarCollectGraphStateKeys.ADAPTIVE_SEARCH_NOTE, note);
      reportProgress(jobId, stepKey, 40, note);
      return;
    }
    if (StrUtil.isBlank(session.getCoreIntentPrompt())) {
      String note = "缺少已确认主要意图，跳过自适应搜索";
      lightState.put(RadarCollectGraphStateKeys.ADAPTIVE_SEARCH_NOTE, note);
      reportProgress(jobId, stepKey, 40, note);
      return;
    }
    // 落库外键要求 channel_id 必须存在于 source_channels；禁止写占位符
    ChannelView searchChannel = resolveThemeSearchChannel(session);
    if (searchChannel == null || StrUtil.isBlank(searchChannel.getId())) {
      String note = "主题无 Tavily/SearXNG 搜索渠道，跳过自适应搜索";
      lightState.put(RadarCollectGraphStateKeys.ADAPTIVE_SEARCH_NOTE, note);
      reportProgress(jobId, stepKey, 40, note);
      return;
    }
    String searchChannelId = searchChannel.getId();
    String searchChannelName = StrUtil.blankToDefault(searchChannel.getName(), searchChannel.getType());
    String searchChannelType = StrUtil.blankToDefault(searchChannel.getType(), ChannelType.SEARXNG.getValue());

    reportProgress(jobId, stepKey, 36, "意图自适应搜索：规划第 1 轮…");
    Set<String> seenUrls = new LinkedHashSet<>();
    for (RadarCollectCandidate existing : session.getCandidates()) {
      if (existing == null) {
        continue;
      }
      String key = StrUtil.blankToDefault(existing.getUrl(), existing.getTitle()).toLowerCase();
      if (StrUtil.isNotBlank(key)) {
        seenUrls.add(key);
      }
    }

    List<String> allTitles = new ArrayList<>();
    int appended = 0;
    List<String> roundNotes = new ArrayList<>();
    AdaptiveSearchExtractAiResult clues = AdaptiveSearchExtractAiResult.builder().build();

    for (int round = 1; round <= MAX_ROUNDS && appended < MAX_APPEND; round++) {
      RadarCollectLeaseSupport.renew(lightState);
      List<String> queries = round == 1
          ? planQueries(session.getCoreIntentPrompt(), null)
          : planQueries(session.getCoreIntentPrompt(), clues);
      if (CollUtil.isEmpty(queries)) {
        roundNotes.add("第" + round + "轮无可用搜索词");
        break;
      }
      reportProgress(
          jobId,
          stepKey,
          36 + round * 2,
          "意图自适应搜索：第 " + round + " 轮执行 " + queries.size() + " 个词…");
      List<FetchedItemView> items = executeSearchChannel(searchChannel, queries, session.getHours());
      int before = appended;
      for (FetchedItemView item : items) {
        if (item == null || appended >= MAX_APPEND) {
          continue;
        }
        String key = StrUtil.blankToDefault(item.getUrl(), item.getTitle()).toLowerCase();
        if (StrUtil.isBlank(key) || !seenUrls.add(key)) {
          continue;
        }
        if (MarketingPageFilter.shouldSkip(item.getUrl(), item.getTitle())) {
          continue;
        }
        boolean video = ResearchContentClassifier.isVideo(item.getUrl(), item.getTitle());
        session.getCandidates().add(RadarCollectCandidate.builder()
            .tempId(IdsUtil.newId())
            .themeId(session.getThemeId())
            .channelId(searchChannelId)
            .channelName(searchChannelName)
            .channelType(searchChannelType)
            .url(item.getUrl())
            .title(item.getTitle())
            .author(item.getAuthor())
            .publishedAt(item.getPublishedAt())
            .fetchSummary(item.getSummary())
            .siteLabel(ResearchContentClassifier.siteLabel(item.getUrl()))
            .searchQuery(item.getSearchQuery())
            .mediaType(video
                ? SourceMediaType.VIDEO.getValue()
                : SourceMediaType.ARTICLE.getValue())
            .build());
        appended++;
        if (StrUtil.isNotBlank(item.getTitle())) {
          allTitles.add(item.getTitle());
        }
      }
      roundNotes.add("第" + round + "轮 queries=" + queries.size()
          + " 新增=" + (appended - before));
      if (round < MAX_ROUNDS && !allTitles.isEmpty()) {
        clues = extractClues(session.getCoreIntentPrompt(), allTitles);
      }
    }

    String note = "自适应搜索完成：追加 " + appended + " 条（" + String.join("；", roundNotes) + "）";
    lightState.put(RadarCollectGraphStateKeys.ADAPTIVE_SEARCH_NOTE, note);
    reportProgress(jobId, stepKey, 42, note);
    log.info("{} themeId={} candidates={}", note, session.getThemeId(), session.getCandidates().size());
  }

  private List<String> planQueries(String intentPrompt, AdaptiveSearchExtractAiResult clues) {
    String system = clues == null ? PLAN_SYSTEM : EXPAND_SYSTEM;
    StringBuilder user = new StringBuilder();
    user.append(intentPrompt).append('\n');
    if (clues != null) {
      user.append("已发现板块：")
          .append(join(clues.getSectors()))
          .append('\n');
      user.append("已发现标的：")
          .append(join(clues.getTickers()))
          .append('\n');
      user.append("已发现人物：")
          .append(join(clues.getPeople()))
          .append('\n');
    }
    user.append("请输出本轮搜索 JSON。");
    AdaptiveSearchPlanAiResult plan = completeJson(
        system, user.toString(), ModelCallScenes.RADAR_ADAPTIVE_SEARCH_PLAN, AdaptiveSearchPlanAiResult.class);
    if (plan == null || CollUtil.isEmpty(plan.getQueries())) {
      return List.of();
    }
    return SearchQueryNormalizer.normalize(plan.getQueries()).stream()
        .limit(MAX_QUERIES_PER_ROUND)
        .toList();
  }

  private AdaptiveSearchExtractAiResult extractClues(String intentPrompt, List<String> titles) {
    StringBuilder user = new StringBuilder();
    user.append(intentPrompt).append("\n\n搜索标题：\n");
    int limit = Math.min(titles.size(), 40);
    for (int index = 0; index < limit; index++) {
      user.append("- ").append(titles.get(index)).append('\n');
    }
    AdaptiveSearchExtractAiResult result = completeJson(
        EXTRACT_SYSTEM,
        user.toString(),
        ModelCallScenes.RADAR_ADAPTIVE_SEARCH_EXTRACT,
        AdaptiveSearchExtractAiResult.class);
    return result == null ? AdaptiveSearchExtractAiResult.builder().build() : result;
  }

  /**
   * 解析主题下可用于落库的联网搜索渠道（Tavily 优先）。
   *
   * @param session 采集会话
   * @return 渠道；无则 null
   */
  private ChannelView resolveThemeSearchChannel(RadarCollectSessionStore.Session session) {
    List<ChannelView> channels = hotNews.loadChannels(session.getThemeId(), session.getChannelIds());
    return searchChannelSelector.resolveOne(channels);
  }

  /**
   * 按选定渠道执行自适应检索。
   *
   * @param channel 选定渠道
   * @param queries 检索词
   * @param hours   时间窗小时
   * @return 命中条目
   */
  private List<FetchedItemView> executeSearchChannel(
      ChannelView channel, List<String> queries, int hours) {
    if (channel == null || CollUtil.isEmpty(queries)) {
      return List.of();
    }
    String type = StrUtil.trim(channel.getType());
    int effectiveHours = hours <= 0 ? 24 : hours;
    try {
      ChannelFetchConfig config;
      String url;
      if (ChannelType.TAVILY.getValue().equalsIgnoreCase(type)) {
        url = StrUtil.blankToDefault(channel.getBaseUrl(), ChannelFetchDefaults.TAVILY_SEARCH_API_URL);
        // Assembler 会复制 config，不改写会话里共享的 channel.config
        config = tavilySearchConfigAssembler.assemble(
            channel.getConfig(),
            TavilySearchContext.builder()
                .scene(TavilySearchScene.RADAR_ADAPTIVE)
                .hours(effectiveHours)
                .queries(new ArrayList<>(queries))
                .build());
        config.setMaxResults(MAX_RESULTS_PER_QUERY);
      } else {
        url = StrUtil.blankToDefault(channel.getBaseUrl(), ChannelFetchDefaults.SEARXNG_DEFAULT_INSTANCE_URL);
        type = ChannelType.SEARXNG.getValue();
        config = ChannelFetchConfig.builder()
            .queries(new ArrayList<>(queries))
            .maxResults(MAX_RESULTS_PER_QUERY)
            .build();
      }
      FetchResult result = fetchFacade.fetch(FetchRequest.builder()
          .url(url)
          .type(type)
          .hours(effectiveHours)
          .timeoutSeconds(ChannelFetchDefaults.DEFAULT_TIMEOUT_SECONDS)
          .config(config)
          .build());
      if (result == null || !result.isOk() || result.getItems() == null) {
        log.warn("自适应搜索失败 type={} err={}",
            type, result == null ? "null" : result.getErrorCode());
        return List.of();
      }
      return result.getItems();
    } catch (Exception exception) {
      log.warn("自适应搜索异常 type={} err={}", type, exception.getMessage());
      return List.of();
    }
  }

  private <T> T completeJson(String system, String user, String scene, Class<T> type) {
    try {
      TextCompleteResult result = ai.completeText(TextCompleteRequest.builder()
          .systemPrompt(system)
          .prompt(user)
          .scene(scene)
          .timeoutSeconds(90)
          .build());
      return LlmJsonSupport.parseObject(result == null ? null : result.getText(), type);
    } catch (Exception exception) {
      log.warn("自适应搜索 AI 失败 scene={} err={}", scene, exception.getMessage());
      return null;
    }
  }

  private void reportProgress(String jobId, String stepKey, int progress, String message) {
    if (StrUtil.isBlank(jobId)) {
      return;
    }
    try {
      jobs.updateProgress(jobId, stepKey, Math.min(95, Math.max(1, progress)), message);
    } catch (Exception exception) {
      log.warn("自适应搜索进度上报失败 jobId={} err={}", jobId, exception.getMessage());
    }
  }

  private static String join(List<String> values) {
    if (CollUtil.isEmpty(values)) {
      return "无";
    }
    return String.join("、", values);
  }

  private static String stringVal(Object value) {
    return value == null ? null : String.valueOf(value);
  }
}
