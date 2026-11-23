package com.chengwenfang.mode.hotnews.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chengwenfang.ai.facade.AiVisionFacade;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.dto.request.VisionCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.VisionCompleteResult;
import com.chengwenfang.crawler.facade.FetchFacade;
import com.chengwenfang.crawler.facade.MarketingPageFilter;
import com.chengwenfang.crawler.facade.constant.ChannelFetchDefaults;
import com.chengwenfang.crawler.facade.dto.request.FetchRequest;
import com.chengwenfang.crawler.facade.dto.view.FetchResult;
import com.chengwenfang.crawler.facade.dto.view.FetchedItemView;
import com.chengwenfang.crawler.facade.dto.view.SearchHitView;
import com.chengwenfang.crawler.facade.enums.ChannelType;
import com.chengwenfang.mode.hotnews.application.model.ResearchCandidate;
import com.chengwenfang.mode.hotnews.application.prompt.ResearchSupplementAgentPrompts;
import com.chengwenfang.mode.hotnews.application.research.ResearchAgentAction;
import com.chengwenfang.mode.hotnews.application.research.ResearchAgentCollectResult;
import com.chengwenfang.mode.hotnews.application.research.ResearchAgentSelectedItem;
import com.chengwenfang.mode.hotnews.application.research.ResearchAgentTurnResponse;
import com.chengwenfang.mode.hotnews.application.research.ResearchAiWebSearchCollectResult;
import com.chengwenfang.mode.hotnews.application.research.ResearchSourcePolicy;
import com.chengwenfang.mode.hotnews.domain.ChineseTextNormalizer;
import com.chengwenfang.mode.hotnews.domain.TopicResearchPlanner;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsResearchAgentLimits;
import com.chengwenfang.mode.hotnews.facade.enums.ResearchAgentActionType;
import com.chengwenfang.mode.hotnews.facade.enums.ResearchItemKind;
import com.chengwenfang.sourcing.domain.entity.SourceChannelEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 选题资料补充：渠道预搜与 AI 联网并行播种命中池，再有界决策循环。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResearchSupplementAgent {

  /** AI 文本门面。 */
  private final AiVisionFacade ai;
  /** 搜索渠道采集。 */
  private final ResearchCandidateCollector collector;
  /** AI 联网搜资料。 */
  private final ResearchAiWebSearchCollector aiWebSearchCollector;
  /** 网页抓取。 */
  private final FetchFacade fetchFacade;

  /**
   * 按选题上下文运行 Agent；未 finish 但已有命中时回退选用命中，否则空候选（由调用方再回退出词搜索）。
   *
   * @param topicTitle          选题标题
   * @param topicSummary        选题摘要
   * @param existingSourceUrls  已有原始来源 URL（禁止重复）
   * @param searchChannels      主题搜索渠道（含 SearXNG）
   * @param sourcePolicy        优选域名等策略；可空
   * @return 采集结果
   */
  public ResearchAgentCollectResult collect(
      String topicTitle,
      String topicSummary,
      List<String> existingSourceUrls,
      List<SourceChannelEntity> searchChannels,
      ResearchSourcePolicy sourcePolicy) {
    String title = StrUtil.nullToEmpty(topicTitle).trim();
    String summary = StrUtil.nullToEmpty(topicSummary).trim();
    if (StrUtil.isBlank(title) && StrUtil.isBlank(summary)) {
      return ResearchAgentCollectResult.builder().usedAgent(false).finished(false).build();
    }
    ResearchSourcePolicy policy = sourcePolicy == null
        ? ResearchSourcePolicy.builder().build()
        : sourcePolicy;

    Set<String> blocked = new LinkedHashSet<>();
    if (existingSourceUrls != null) {
      for (String url : existingSourceUrls) {
        String key = TopicResearchPlanner.normalizeUrl(url);
        if (StrUtil.isNotBlank(key)) {
          blocked.add(key);
        }
      }
    }

    Map<String, SearchHitView> hitByUrl = new LinkedHashMap<>();
    Map<String, String> fetchedSummaryByUrl = new LinkedHashMap<>();
    List<String> queriesRun = new ArrayList<>();
    StringBuilder toolLog = new StringBuilder();
    int rounds = 0;

    boolean tavilyOnly = collector.isTavilyOnly(searchChannels);
    try {
      // 渠道预搜与 AI 联网并行播种命中池，再进入决策循环
      seedHitsInParallel(
          title, summary, searchChannels, blocked, hitByUrl, queriesRun, toolLog, policy, tavilyOnly);

      for (int round = 1; round <= HotNewsResearchAgentLimits.MAX_ROUNDS; round++) {
        rounds = round;
        ResearchAgentTurnResponse turn =
            decide(title, summary, blocked, hitByUrl, fetchedSummaryByUrl, toolLog, policy, tavilyOnly);
        if (turn == null || CollUtil.isEmpty(turn.getActions())) {
          log.warn("资料补充 Agent 第{}轮无有效动作 title={}", round, title);
          break;
        }

        // 同一轮内先 search/fetch，再 finish，避免模型把 finish 写在搜索前面导致空选
        List<ResearchAgentAction> searchActions = new ArrayList<>();
        List<ResearchAgentAction> fetchActions = new ArrayList<>();
        List<ResearchAgentAction> finishActions = new ArrayList<>();
        for (ResearchAgentAction action : turn.getActions()) {
          ResearchAgentActionType type = ResearchAgentActionType.from(action.getType());
          if (type == ResearchAgentActionType.SEARCH) {
            searchActions.add(action);
          } else if (type == ResearchAgentActionType.FETCH) {
            fetchActions.add(action);
          } else if (type == ResearchAgentActionType.FINISH) {
            finishActions.add(action);
          }
        }
        for (ResearchAgentAction action : searchActions) {
          List<String> queries = action.getQueries();
          if (!tavilyOnly && !policy.isOpenSearchEnabled() && policy.hasPreferredDomains()) {
            queries = onlySiteScopedQueries(queries, policy);
          }
          runSearch(queries, searchChannels, blocked, hitByUrl, queriesRun, toolLog, policy);
        }
        for (ResearchAgentAction action : fetchActions) {
          runFetch(action.getUrls(), hitByUrl, fetchedSummaryByUrl, toolLog, policy);
        }

        boolean wantFinish = !finishActions.isEmpty();
        List<ResearchCandidate> selected = List.of();
        if (wantFinish) {
          // 多条 finish 时合并选用（仍受条数上限）
          List<ResearchAgentSelectedItem> finishItems = new ArrayList<>();
          for (ResearchAgentAction action : finishActions) {
            if (action.getItems() != null) {
              finishItems.addAll(action.getItems());
            }
          }
          selected = resolveFinish(finishItems, hitByUrl, fetchedSummaryByUrl, blocked, policy);
        }

        log.info(
            "资料补充 Agent 轮次 round={} queriesRun={} hits={} wantFinish={} selected={}",
            round,
            queriesRun.size(),
            hitByUrl.size(),
            wantFinish,
            selected.size());

        if (wantFinish && !selected.isEmpty()) {
          return ResearchAgentCollectResult.builder()
              .usedAgent(true)
              .finished(true)
              .rounds(rounds)
              .queriesRun(queriesRun)
              .hitCount(hitByUrl.size())
              .webCandidates(selected)
              .build();
        }
        if (wantFinish && selected.isEmpty()) {
          toolLog.append("finish 未选中工具命中中的有效 URL，请基于 knownHits 重选或继续 search。\n");
        }
        if (hitByUrl.size() >= HotNewsResearchAgentLimits.MAX_HITS) {
          toolLog.append("命中已达上限，请 finish 并只从 knownHits 选 URL。\n");
        }
      }
    } catch (Exception exception) {
      log.warn("资料补充 Agent 异常 title={} err={}", title, exception.getMessage());
    }

    // 模型未 finish 成功时，仍把已检索且未屏蔽的命中交给后续核验，避免白跑 SearXNG
    List<ResearchCandidate> leftover = promoteHits(hitByUrl, fetchedSummaryByUrl, blocked, policy);
    if (!leftover.isEmpty()) {
      log.info("资料补充 Agent 未 finish，回退选用命中 count={}", leftover.size());
      return ResearchAgentCollectResult.builder()
          .usedAgent(true)
          .finished(true)
          .rounds(rounds)
          .queriesRun(queriesRun)
          .hitCount(hitByUrl.size())
          .webCandidates(leftover)
          .build();
    }
    return ResearchAgentCollectResult.builder()
        .usedAgent(true)
        .finished(false)
        .rounds(rounds)
        .queriesRun(queriesRun)
        .hitCount(hitByUrl.size())
        .webCandidates(List.of())
        .build();
  }

  /**
   * 并行执行渠道预搜与 AI 联网搜，合并写入命中池。
   *
   * @param title          选题标题
   * @param summary        选题摘要
   * @param searchChannels 搜索渠道
   * @param blocked        已占用 URL
   * @param hitByUrl       命中池
   * @param queriesRun     已跑检索词
   * @param toolLog        工具日志
   * @param policy         来源策略
   * @param tavilyOnly     是否仅 Tavily
   */
  private void seedHitsInParallel(
      String title,
      String summary,
      List<SourceChannelEntity> searchChannels,
      Set<String> blocked,
      Map<String, SearchHitView> hitByUrl,
      List<String> queriesRun,
      StringBuilder toolLog,
      ResearchSourcePolicy policy,
      boolean tavilyOnly) {
    String subject = TopicResearchPlanner.extractShortQuery(
        TopicResearchPlanner.extractSubject(title));
    if (StrUtil.isBlank(subject)) {
      subject = StrUtil.maxLength(StrUtil.nullToEmpty(title).trim(), 32);
    }
    boolean useTavilyAngles = tavilyOnly;
    String seedSubject = subject;
    // 专用短生命周期池：避免长耗时联网任务占满 commonPool
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      CompletableFuture<ChannelSeedResult> channelFuture = CompletableFuture.supplyAsync(() -> {
        try {
          Map<String, SearchHitView> channelHits = new LinkedHashMap<>();
          List<String> channelQueries = new ArrayList<>();
          StringBuilder channelLog = new StringBuilder();
          List<String> seedQueries = useTavilyAngles
              ? ResearchSourcePolicyResolver.buildAngleQueries(seedSubject, policy)
              : ResearchSourcePolicyResolver.buildSiteScopedQueries(seedSubject, policy);
          if (!seedQueries.isEmpty()) {
            channelLog.append(useTavilyAngles ? "preferred angle queries=" : "preferred site queries=")
                .append(seedQueries.size())
                .append('\n');
            for (int index = 0;
                index < seedQueries.size();
                index += HotNewsResearchAgentLimits.MAX_QUERIES_PER_ROUND) {
              int end = Math.min(
                  index + HotNewsResearchAgentLimits.MAX_QUERIES_PER_ROUND, seedQueries.size());
              runSearch(
                  seedQueries.subList(index, end),
                  searchChannels,
                  blocked,
                  channelHits,
                  channelQueries,
                  channelLog,
                  policy);
            }
          }
          return new ChannelSeedResult(channelHits, channelQueries, channelLog.toString());
        } catch (Exception exception) {
          log.warn("资料补充渠道预搜失败 title={} err={}", title, exception.getMessage());
          return new ChannelSeedResult(
              Map.of(),
              List.of(),
              "channel seed failed=" + StrUtil.blankToDefault(exception.getMessage(), "unknown") + '\n');
        }
      }, pool);

      CompletableFuture<ResearchAiWebSearchCollectResult> aiFuture = CompletableFuture.supplyAsync(
          () -> {
            try {
              return aiWebSearchCollector.collect(title, summary, blocked, policy);
            } catch (Exception exception) {
              log.warn("资料补充 AI 联网预搜线程失败 title={} err={}", title, exception.getMessage());
              return ResearchAiWebSearchCollectResult.builder()
                  .used(false)
                  .skipReason("AI 联网线程失败："
                      + StrUtil.blankToDefault(exception.getMessage(), "unknown"))
                  .build();
            }
          },
          pool);

      // 两侧各自降级，互不拖垮；再按配额合并
      ChannelSeedResult channelSeed = channelFuture
          .exceptionally(exception -> {
            log.warn("资料补充渠道预搜 Future 失败 title={} err={}", title, exception.getMessage());
            return new ChannelSeedResult(
                Map.of(),
                List.of(),
                "channel seed future failed="
                    + StrUtil.blankToDefault(exception.getMessage(), "unknown")
                    + '\n');
          })
          .join();
      ResearchAiWebSearchCollectResult aiSeed = aiFuture
          .exceptionally(exception -> {
            log.warn("资料补充 AI 联网 Future 失败 title={} err={}", title, exception.getMessage());
            return ResearchAiWebSearchCollectResult.builder()
                .used(false)
                .skipReason("AI 联网 Future 失败："
                    + StrUtil.blankToDefault(exception.getMessage(), "unknown"))
                .build();
          })
          .join();

      // 合并/记日志失败不抛出：已入池命中保留，继续进入 decide
      try {
        applyParallelSeedResults(channelSeed, aiSeed, blocked, policy, hitByUrl, queriesRun, toolLog);
      } catch (Exception exception) {
        log.warn("资料补充播种合并失败 title={} poolSize={} err={}",
            title, hitByUrl.size(), exception.getMessage());
        toolLog.append("seed merge failed=")
            .append(StrUtil.blankToDefault(exception.getMessage(), "unknown"))
            .append(" keptHits=")
            .append(hitByUrl.size())
            .append('\n');
      }
    } finally {
      pool.shutdown();
      try {
        if (!pool.awaitTermination(5, TimeUnit.MINUTES)) {
          pool.shutdownNow();
        }
      } catch (InterruptedException interrupted) {
        pool.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
  }

  /**
   * 将两侧播种结果写入命中池与工具日志（供 decide 使用）。
   *
   * @param channelSeed 渠道预搜结果
   * @param aiSeed      AI 联网结果；可空
   * @param blocked     已占用 URL
   * @param policy      来源策略
   * @param hitByUrl    命中池
   * @param queriesRun  已跑检索词
   * @param toolLog     工具日志
   */
  static void applyParallelSeedResults(
      ChannelSeedResult channelSeed,
      ResearchAiWebSearchCollectResult aiSeed,
      Set<String> blocked,
      ResearchSourcePolicy policy,
      Map<String, SearchHitView> hitByUrl,
      List<String> queriesRun,
      StringBuilder toolLog) {
    ChannelSeedResult safeChannel = channelSeed == null
        ? new ChannelSeedResult(Map.of(), List.of(), "")
        : channelSeed;
    for (String query : safeChannel.queries()) {
      if (StrUtil.isNotBlank(query) && !queriesRun.contains(query)) {
        queriesRun.add(query);
      }
    }
    toolLog.append(StrUtil.nullToEmpty(safeChannel.log()));

    List<SearchHitView> aiHits = aiSeed == null || aiSeed.getHits() == null
        ? List.of()
        : aiSeed.getHits();
    int before = hitByUrl.size();
    mergeParallelSeedHits(safeChannel.hits(), aiHits, blocked, policy, hitByUrl);
    int addedTotal = hitByUrl.size() - before;

    if (aiSeed == null) {
      toolLog.append("ai_web_search: 空结果\n");
      return;
    }
    if (!aiSeed.isUsed() || CollUtil.isEmpty(aiHits)) {
      toolLog.append("ai_web_search skipped=")
          .append(StrUtil.blankToDefault(aiSeed.getSkipReason(), "no_hits"))
          .append(" citations=")
          .append(aiSeed.getCitationCount())
          .append(" poolAdded=")
          .append(addedTotal)
          .append(" totalHits=")
          .append(hitByUrl.size())
          .append('\n');
      log.info(
          "资料补充 AI 联网播种跳过 used={} reason={} citations={} poolSize={}",
          aiSeed.isUsed(),
          aiSeed.getSkipReason(),
          aiSeed.getCitationCount(),
          hitByUrl.size());
      return;
    }
    toolLog.append("ai_web_search citations=")
        .append(aiSeed.getCitationCount())
        .append(" aiHits=")
        .append(aiHits.size())
        .append(" poolAdded=")
        .append(addedTotal)
        .append(" totalHits=")
        .append(hitByUrl.size())
        .append('\n');
  }

  /**
   * 并行播种结果合并：按「可入池」AI 条数预留配额，渠道先填剩余；AI 写入后若有空位再回填渠道。
   *
   * @param channelHits 渠道命中
   * @param aiHits      AI 联网命中
   * @param blocked     已占用 URL
   * @param policy      来源策略；可空
   * @param hitByUrl    目标命中池
   */
  static void mergeParallelSeedHits(
      Map<String, SearchHitView> channelHits,
      List<SearchHitView> aiHits,
      Set<String> blocked,
      ResearchSourcePolicy policy,
      Map<String, SearchHitView> hitByUrl) {
    Map<String, SearchHitView> aiAdmissible = takeAdmissibleHits(
        toHitMap(aiHits),
        blocked,
        policy,
        HotNewsResearchAgentLimits.MAX_AI_WEB_SEARCH_ITEMS);
    int aiReserve = aiAdmissible.size();
    int channelCap = Math.max(0, HotNewsResearchAgentLimits.MAX_HITS - aiReserve);
    mergeHits(channelHits, blocked, policy, hitByUrl, channelCap);
    mergeHits(aiAdmissible, blocked, policy, hitByUrl, HotNewsResearchAgentLimits.MAX_HITS);
    // 与渠道撞车等导致预留未用尽时，回填渠道占满剩余名额
    if (hitByUrl.size() < HotNewsResearchAgentLimits.MAX_HITS) {
      mergeHits(channelHits, blocked, policy, hitByUrl, HotNewsResearchAgentLimits.MAX_HITS);
    }
  }

  /**
   * 筛选可通过占用/屏蔽/营销过滤的命中，并截断条数。
   *
   * @param source   来源
   * @param blocked  已占用 URL
   * @param policy   来源策略
   * @param maxItems 上限
   * @return 可入池命中
   */
  static Map<String, SearchHitView> takeAdmissibleHits(
      Map<String, SearchHitView> source,
      Set<String> blocked,
      ResearchSourcePolicy policy,
      int maxItems) {
    Map<String, SearchHitView> out = new LinkedHashMap<>();
    if (source == null || source.isEmpty() || maxItems <= 0) {
      return out;
    }
    for (Map.Entry<String, SearchHitView> entry : source.entrySet()) {
      if (out.size() >= maxItems) {
        break;
      }
      SearchHitView hit = entry.getValue();
      String urlKey = entry.getKey();
      if (!isAdmissibleHit(urlKey, hit, blocked, policy, out)) {
        continue;
      }
      out.put(urlKey, hit);
    }
    return out;
  }

  /**
   * 将命中列表转为规范化 URL 映射。
   *
   * @param hits 命中
   * @return 映射
   */
  static Map<String, SearchHitView> toHitMap(List<SearchHitView> hits) {
    Map<String, SearchHitView> map = new LinkedHashMap<>();
    if (hits == null) {
      return map;
    }
    for (SearchHitView hit : hits) {
      if (hit == null || StrUtil.isBlank(hit.getUrl())) {
        continue;
      }
      String key = TopicResearchPlanner.normalizeUrl(hit.getUrl());
      if (StrUtil.isNotBlank(key)) {
        map.putIfAbsent(key, hit);
      }
    }
    return map;
  }

  /**
   * 合并命中到共享池（去重、占用/屏蔽域、营销过滤、池上限）。
   *
   * @param source     来源命中
   * @param blocked    已占用 URL
   * @param policy     来源策略；可空
   * @param hitByUrl   目标池
   * @param maxPoolSize 本池允许的最大条数
   * @return 新写入条数
   */
  static int mergeHits(
      Map<String, SearchHitView> source,
      Set<String> blocked,
      ResearchSourcePolicy policy,
      Map<String, SearchHitView> hitByUrl,
      int maxPoolSize) {
    if (source == null || source.isEmpty() || maxPoolSize <= 0) {
      return 0;
    }
    int added = 0;
    for (Map.Entry<String, SearchHitView> entry : source.entrySet()) {
      if (hitByUrl.size() >= maxPoolSize) {
        break;
      }
      SearchHitView hit = entry.getValue();
      String urlKey = entry.getKey();
      if (!isAdmissibleHit(urlKey, hit, blocked, policy, hitByUrl)) {
        continue;
      }
      hitByUrl.put(urlKey, hit);
      added++;
    }
    return added;
  }

  /**
   * 命中是否可写入目标池（占用/重复/屏蔽域/营销页）。
   *
   * @param urlKey   规范化 URL
   * @param hit      命中
   * @param blocked  已占用 URL
   * @param policy   来源策略
   * @param present  已存在于池中的键集合（目标池或预筛集合）
   * @return 可写入时 true
   */
  static boolean isAdmissibleHit(
      String urlKey,
      SearchHitView hit,
      Set<String> blocked,
      ResearchSourcePolicy policy,
      Map<String, SearchHitView> present) {
    if (hit == null || StrUtil.isBlank(hit.getUrl()) || StrUtil.isBlank(urlKey)) {
      return false;
    }
    if (blocked != null && blocked.contains(urlKey)) {
      return false;
    }
    if (present != null && present.containsKey(urlKey)) {
      return false;
    }
    if (ResearchSourcePolicyResolver.isBlockedUrl(hit.getUrl(), policy)) {
      return false;
    }
    return !MarketingPageFilter.shouldSkip(hit.getUrl(), hit.getTitle());
  }

  /**
   * 渠道预搜线程结果。
   *
   * @param hits    命中
   * @param queries 检索词
   * @param log     工具日志片段
   */
  record ChannelSeedResult(
      Map<String, SearchHitView> hits,
      List<String> queries,
      String log) {}

  /**
   * 将命中池转为候选（跳过已有来源 URL、屏蔽域、营销页）。
   *
   * @param hitByUrl              命中池
   * @param fetchedSummaryByUrl   已抓正文摘要
   * @param blocked               已占用 URL
   * @param policy                来源策略
   * @return 候选列表
   */
  private List<ResearchCandidate> promoteHits(
      Map<String, SearchHitView> hitByUrl,
      Map<String, String> fetchedSummaryByUrl,
      Set<String> blocked,
      ResearchSourcePolicy policy) {
    if (hitByUrl == null || hitByUrl.isEmpty()) {
      return List.of();
    }
    List<ResearchAgentSelectedItem> items = new ArrayList<>();
    for (Map.Entry<String, SearchHitView> entry : hitByUrl.entrySet()) {
      SearchHitView hit = entry.getValue();
      if (hit == null || StrUtil.isBlank(hit.getUrl())) {
        continue;
      }
      items.add(ResearchAgentSelectedItem.builder()
          .url(hit.getUrl())
          .title(hit.getTitle())
          .reason("agent_hit_fallback")
          .build());
    }
    return resolveFinish(items, hitByUrl, fetchedSummaryByUrl, blocked, policy);
  }

  /**
   * 解析 Agent 单轮 JSON。
   *
   * @param raw 模型原文
   * @return 回合响应；失败 null
   */
  static ResearchAgentTurnResponse parseTurn(String raw) {
    JSONObject json = extractJsonObject(raw);
    if (json == null) {
      return null;
    }
    JSONArray actionsJson = json.getJSONArray("actions");
    if (actionsJson == null || actionsJson.isEmpty()) {
      return null;
    }
    List<ResearchAgentAction> actions = new ArrayList<>();
    for (Object entry : actionsJson) {
      if (!(entry instanceof JSONObject actionJson)) {
        continue;
      }
      String type = StrUtil.trim(actionJson.getStr("type"));
      if (StrUtil.isBlank(type)) {
        continue;
      }
      ResearchAgentAction.ResearchAgentActionBuilder builder = ResearchAgentAction.builder().type(type);
      JSONArray queriesJson = actionJson.getJSONArray("queries");
      if (queriesJson != null) {
        List<String> queries = new ArrayList<>();
        for (Object queryItem : queriesJson) {
          String query = StrUtil.trim(String.valueOf(queryItem));
          if (StrUtil.isNotBlank(query) && !queries.contains(query)) {
            queries.add(query);
          }
        }
        builder.queries(queries);
      }
      JSONArray urlsJson = actionJson.getJSONArray("urls");
      if (urlsJson != null) {
        List<String> urls = new ArrayList<>();
        for (Object urlItem : urlsJson) {
          String url = StrUtil.trim(String.valueOf(urlItem));
          if (StrUtil.isNotBlank(url) && !urls.contains(url)) {
            urls.add(url);
          }
        }
        builder.urls(urls);
      }
      JSONArray itemsJson = actionJson.getJSONArray("items");
      if (itemsJson != null) {
        List<ResearchAgentSelectedItem> items = new ArrayList<>();
        for (Object itemObj : itemsJson) {
          if (!(itemObj instanceof JSONObject itemJson)) {
            continue;
          }
          String url = StrUtil.trim(itemJson.getStr("url"));
          if (StrUtil.isBlank(url)) {
            continue;
          }
          items.add(ResearchAgentSelectedItem.builder()
              .url(url)
              .title(StrUtil.trim(itemJson.getStr("title")))
              .reason(StrUtil.trim(itemJson.getStr("reason")))
              .build());
        }
        builder.items(items);
      }
      actions.add(builder.build());
    }
    if (actions.isEmpty()) {
      return null;
    }
    return ResearchAgentTurnResponse.builder().actions(actions).build();
  }

  private ResearchAgentTurnResponse decide(
      String title,
      String summary,
      Set<String> blocked,
      Map<String, SearchHitView> hitByUrl,
      Map<String, String> fetchedSummaryByUrl,
      StringBuilder toolLog,
      ResearchSourcePolicy policy,
      boolean tavilyOnly) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("title", title);
    payload.put("summary", summary.length() > 1200 ? summary.substring(0, 1200) : summary);
    payload.put("excludedUrls", new ArrayList<>(blocked).stream().limit(30).toList());
    payload.put("preferredDomains", policy.getPreferredDomains());
    payload.put("blockedDomains", policy.getBlockedDomains());
    payload.put("queryAngles", policy.getQueryAngles());
    payload.put("domainFilterMode", tavilyOnly ? "tavily_params" : "site_query");
    payload.put("knownHits", summarizeHits(hitByUrl, fetchedSummaryByUrl));
    payload.put("toolLog", toolLog.toString());
    payload.put(
        "budget",
        Map.of(
            "maxRounds", HotNewsResearchAgentLimits.MAX_ROUNDS,
            "maxQueriesPerRound", HotNewsResearchAgentLimits.MAX_QUERIES_PER_ROUND,
            "maxHits", HotNewsResearchAgentLimits.MAX_HITS,
            "currentHits", hitByUrl.size()));
    VisionCompleteResult result = ai.complete(VisionCompleteRequest.builder()
        .scene(ModelCallScenes.HOT_NEWS_TOPIC_RESEARCH_AGENT)
        .systemPrompt(ResearchSupplementAgentPrompts.SYSTEM)
        .prompt(JSONUtil.toJsonStr(payload))
        .timeoutSeconds(90)
        .build());
    return parseTurn(result == null ? null : result.getText());
  }

  private void runSearch(
      List<String> queries,
      List<SourceChannelEntity> searchChannels,
      Set<String> blocked,
      Map<String, SearchHitView> hitByUrl,
      List<String> queriesRun,
      StringBuilder toolLog,
      ResearchSourcePolicy policy) {
    if (CollUtil.isEmpty(queries)) {
      toolLog.append("search: 空 queries\n");
      return;
    }
    int accepted = 0;
    int queryCount = 0;
    for (String query : queries) {
      if (queryCount >= HotNewsResearchAgentLimits.MAX_QUERIES_PER_ROUND) {
        break;
      }
      if (StrUtil.isBlank(query)) {
        continue;
      }
      queryCount++;
      if (!queriesRun.contains(query)) {
        queriesRun.add(query);
      }
      List<SearchHitView> hits = collector.searchViaConfiguredChannels(
          searchChannels, query, HotNewsResearchAgentLimits.SEARCH_LIMIT_PER_QUERY, policy);
      int added = 0;
      for (SearchHitView hit : hits) {
        if (hit == null || StrUtil.isBlank(hit.getUrl())) {
          continue;
        }
        if (hitByUrl.size() >= HotNewsResearchAgentLimits.MAX_HITS) {
          break;
        }
        String urlKey = TopicResearchPlanner.normalizeUrl(hit.getUrl());
        if (StrUtil.isBlank(urlKey) || blocked.contains(urlKey) || hitByUrl.containsKey(urlKey)) {
          continue;
        }
        if (ResearchSourcePolicyResolver.isBlockedUrl(hit.getUrl(), policy)) {
          continue;
        }
        if (MarketingPageFilter.shouldSkip(hit.getUrl(), hit.getTitle())) {
          continue;
        }
        hitByUrl.put(urlKey, hit);
        added++;
        accepted++;
      }
      toolLog.append("search q=")
          .append(query)
          .append(" added=")
          .append(added)
          .append(" totalHits=")
          .append(hitByUrl.size())
          .append('\n');
    }
    if (accepted == 0) {
      toolLog.append("search: 本轮无新命中，请换角度再搜或 finish。\n");
    }
  }

  private void runFetch(
      List<String> urls,
      Map<String, SearchHitView> hitByUrl,
      Map<String, String> fetchedSummaryByUrl,
      StringBuilder toolLog,
      ResearchSourcePolicy policy) {
    if (CollUtil.isEmpty(urls)) {
      toolLog.append("fetch: 空 urls\n");
      return;
    }
    int count = 0;
    for (String url : urls) {
      if (count >= HotNewsResearchAgentLimits.MAX_FETCH_PER_ROUND) {
        break;
      }
      if (ResearchSourcePolicyResolver.isBlockedUrl(url, policy)) {
        toolLog.append("fetch skip blocked url=").append(url).append('\n');
        continue;
      }
      String urlKey = TopicResearchPlanner.normalizeUrl(url);
      SearchHitView hit = hitByUrl.get(urlKey);
      if (hit == null) {
        toolLog.append("fetch skip unknown url=").append(url).append('\n');
        continue;
      }
      count++;
      try {
        FetchResult fetched = fetchFacade.fetch(FetchRequest.builder()
            .url(hit.getUrl())
            .type(ChannelType.WEB.getValue())
            .timeoutSeconds(ChannelFetchDefaults.QUICK_WEB_TIMEOUT_SECONDS)
            .build());
        if (fetched.isOk() && fetched.getItems() != null && !fetched.getItems().isEmpty()) {
          FetchedItemView item = fetched.getItems().get(0);
          if (StrUtil.isNotBlank(item.getTitle())) {
            hitByUrl.put(urlKey, new SearchHitView(
                item.getTitle(),
                hit.getUrl(),
                StrUtil.blankToDefault(item.getSummary(), hit.getSnippet())));
          }
          if (StrUtil.isNotBlank(item.getSummary())) {
            fetchedSummaryByUrl.put(urlKey, item.getSummary());
          }
          toolLog.append("fetch ok url=")
              .append(hit.getUrl())
              .append(" title=")
              .append(StrUtil.maxLength(hitByUrl.get(urlKey).getTitle(), 40))
              .append('\n');
        } else {
          toolLog.append("fetch empty url=").append(hit.getUrl()).append('\n');
        }
      } catch (Exception exception) {
        toolLog.append("fetch err url=")
            .append(hit.getUrl())
            .append(" msg=")
            .append(exception.getMessage())
            .append('\n');
      }
    }
  }

  private List<ResearchCandidate> resolveFinish(
      List<ResearchAgentSelectedItem> items,
      Map<String, SearchHitView> hitByUrl,
      Map<String, String> fetchedSummaryByUrl,
      Set<String> blocked,
      ResearchSourcePolicy policy) {
    if (CollUtil.isEmpty(items)) {
      return List.of();
    }
    List<ResearchCandidate> out = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    for (ResearchAgentSelectedItem item : items) {
      if (out.size() >= HotNewsResearchAgentLimits.MAX_FINISH_ITEMS) {
        break;
      }
      if (item == null || StrUtil.isBlank(item.getUrl())) {
        continue;
      }
      String urlKey = TopicResearchPlanner.normalizeUrl(item.getUrl());
      if (StrUtil.isBlank(urlKey) || blocked.contains(urlKey) || !seen.add(urlKey)) {
        continue;
      }
      if (ResearchSourcePolicyResolver.isBlockedUrl(item.getUrl(), policy)) {
        continue;
      }
      SearchHitView hit = hitByUrl.get(urlKey);
      if (hit == null) {
        log.warn("资料补充 Agent finish 忽略未命中 url={}", item.getUrl());
        continue;
      }
      String title = StrUtil.firstNonBlank(item.getTitle(), hit.getTitle(), hit.getUrl());
      if (MarketingPageFilter.shouldSkip(hit.getUrl(), title)) {
        continue;
      }
      String summary = StrUtil.firstNonBlank(
          fetchedSummaryByUrl.get(urlKey),
          hit.getSnippet(),
          item.getReason());
      out.add(ResearchCandidate.builder()
          .title(ChineseTextNormalizer.toSimplified(title))
          .url(hit.getUrl())
          .summary(ChineseTextNormalizer.toSimplified(StrUtil.nullToEmpty(summary)))
          .kind(ResearchItemKind.WEB)
          .build());
    }
    return out;
  }

  private static List<String> onlySiteScopedQueries(
      List<String> queries, ResearchSourcePolicy policy) {
    if (CollUtil.isEmpty(queries) || policy == null || !policy.hasPreferredDomains()) {
      return queries == null ? List.of() : queries;
    }
    List<String> kept = new ArrayList<>();
    for (String query : queries) {
      if (StrUtil.isBlank(query)) {
        continue;
      }
      String lower = query.toLowerCase();
      boolean matched = false;
      for (String domain : policy.getPreferredDomains()) {
        if (StrUtil.isNotBlank(domain) && lower.contains("site:" + domain.toLowerCase())) {
          matched = true;
          break;
        }
      }
      if (matched) {
        kept.add(query);
      }
    }
    return kept;
  }

  private static List<Map<String, String>> summarizeHits(
      Map<String, SearchHitView> hitByUrl,
      Map<String, String> fetchedSummaryByUrl) {
    List<Map<String, String>> rows = new ArrayList<>();
    for (Map.Entry<String, SearchHitView> entry : hitByUrl.entrySet()) {
      SearchHitView hit = entry.getValue();
      Map<String, String> row = new LinkedHashMap<>();
      row.put("url", hit.getUrl());
      row.put("title", StrUtil.nullToEmpty(hit.getTitle()));
      String summary = StrUtil.firstNonBlank(fetchedSummaryByUrl.get(entry.getKey()), hit.getSnippet());
      if (StrUtil.isNotBlank(summary) && summary.length() > 180) {
        summary = summary.substring(0, 180);
      }
      row.put("snippet", StrUtil.nullToEmpty(summary));
      rows.add(row);
      if (rows.size() >= HotNewsResearchAgentLimits.MAX_HITS) {
        break;
      }
    }
    return rows;
  }

  private static JSONObject extractJsonObject(String raw) {
    if (StrUtil.isBlank(raw)) {
      return null;
    }
    try {
      String text = raw.trim();
      int start = text.indexOf('{');
      int end = text.lastIndexOf('}');
      if (start >= 0 && end > start) {
        text = text.substring(start, end + 1);
      }
      return JSONUtil.parseObj(text);
    } catch (Exception ignored) {
      return null;
    }
  }
}
