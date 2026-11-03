package com.chengwenfang.mode.hotnews.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chengwenfang.ai.facade.AiTextFacade;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.dto.request.TextCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.TextCompleteResult;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 选题资料补充：有界 AI 决策循环 + SearXNG/搜索渠道执行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResearchSupplementAgent {

  /** AI 文本门面。 */
  private final AiTextFacade ai;
  /** 搜索渠道采集。 */
  private final ResearchCandidateCollector collector;
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

    try {
      // 预检索：Tavily 用短角度词（域名走 API 参数）；SearXNG 仍用 site:
      String subject = TopicResearchPlanner.extractShortQuery(
          TopicResearchPlanner.extractSubject(title));
      if (StrUtil.isBlank(subject)) {
        subject = StrUtil.maxLength(StrUtil.nullToEmpty(title).trim(), 32);
      }
      boolean tavilyOnly = collector.isTavilyOnly(searchChannels);
      List<String> seedQueries = tavilyOnly
          ? ResearchSourcePolicyResolver.buildAngleQueries(subject, policy)
          : ResearchSourcePolicyResolver.buildSiteScopedQueries(subject, policy);
      if (!seedQueries.isEmpty()) {
        toolLog.append(tavilyOnly ? "preferred angle queries=" : "preferred site queries=")
            .append(seedQueries.size())
            .append('\n');
        for (int index = 0; index < seedQueries.size(); index += HotNewsResearchAgentLimits.MAX_QUERIES_PER_ROUND) {
          int end = Math.min(index + HotNewsResearchAgentLimits.MAX_QUERIES_PER_ROUND, seedQueries.size());
          runSearch(
              seedQueries.subList(index, end),
              searchChannels,
              blocked,
              hitByUrl,
              queriesRun,
              toolLog,
              policy);
        }
      }

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
    TextCompleteResult result = ai.completeText(TextCompleteRequest.builder()
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
