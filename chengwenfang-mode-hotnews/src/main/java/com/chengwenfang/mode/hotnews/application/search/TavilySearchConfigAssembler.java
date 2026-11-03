package com.chengwenfang.mode.hotnews.application.search;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.crawler.facade.constant.UpstreamJsonFields;
import com.chengwenfang.crawler.facade.dto.request.ChannelFetchConfig;
import com.chengwenfang.crawler.facade.enums.TavilyTopic;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsResearchSourceDefaults;
import com.chengwenfang.mode.hotnews.facade.enums.TavilySearchScene;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Tavily {@link ChannelFetchConfig} 装配器：按场景写入域名/时间窗/正文等共同规则。
 */
@Component
public class TavilySearchConfigAssembler {

  /**
   * 在渠道基础配置上装配本次 Tavily 请求参数（不修改入参）。
   *
   * @param base    渠道配置；可空
   * @param context 场景上下文；可空则按 {@link TavilySearchScene#RADAR_FIXED} 最小默认
   * @return 新配置
   */
  public ChannelFetchConfig assemble(ChannelFetchConfig base, TavilySearchContext context) {
    ChannelFetchConfig source = base == null ? new ChannelFetchConfig() : base;
    TavilySearchContext safe = context == null
        ? TavilySearchContext.builder().scene(TavilySearchScene.RADAR_FIXED).build()
        : context;
    TavilySearchScene scene = safe.getScene() == null ? TavilySearchScene.RADAR_FIXED : safe.getScene();

    ChannelFetchConfig out = ChannelFetchConfig.builder()
        .query(source.getQuery())
        .queries(copyList(source.getQueries()))
        .urls(copyList(source.getUrls()))
        .maxResults(source.getMaxResults())
        .topic(source.getTopic())
        .searchDepth(source.getSearchDepth())
        .apiKey(source.getApiKey())
        .dateField(source.getDateField())
        .sort(source.getSort())
        .includeDomains(copyList(source.getIncludeDomains()))
        .excludeDomains(copyList(source.getExcludeDomains()))
        .timeRange(source.getTimeRange())
        .includeRawContent(source.getIncludeRawContent())
        .build();

    if (CollUtil.isNotEmpty(safe.getQueries())) {
      List<String> capped = new ArrayList<>();
      for (String query : safe.getQueries()) {
        String trimmed = StrUtil.trim(query);
        if (StrUtil.isBlank(trimmed)) {
          continue;
        }
        capped.add(capQuery(trimmed));
      }
      out.setQueries(capped);
      out.setQuery(capped.isEmpty() ? null : capped.get(0));
    } else if (StrUtil.isNotBlank(out.getQuery())) {
      out.setQuery(capQuery(out.getQuery().trim()));
    }

    Set<String> include = new LinkedHashSet<>();
    addHosts(include, out.getIncludeDomains());
    addHosts(include, safe.getIncludeDomains());
    out.setIncludeDomains(new ArrayList<>(include));

    Set<String> exclude = new LinkedHashSet<>();
    addHosts(exclude, HotNewsResearchSourceDefaults.DEFAULT_BLOCKED_DOMAINS);
    addHosts(exclude, out.getExcludeDomains());
    addHosts(exclude, safe.getExcludeDomains());
    out.setExcludeDomains(new ArrayList<>(exclude));

    if (StrUtil.isBlank(out.getTimeRange())) {
      out.setTimeRange(resolveTimeRange(scene, safe.getHours()));
    }
    if (out.getIncludeRawContent() == null) {
      out.setIncludeRawContent(true);
    }
    if (safe.isFinanceTopic()) {
      out.setTopic(TavilyTopic.FINANCE.getValue());
    }
    return out;
  }

  /**
   * 时间窗映射：补搜固定 week；其余按 hours（≤36→day，≤10d→week，否则 month）。
   *
   * @param scene 场景
   * @param hours 小时；可空
   * @return time_range
   */
  static String resolveTimeRange(TavilySearchScene scene, Integer hours) {
    if (scene == TavilySearchScene.TOPIC_RESEARCH) {
      return UpstreamJsonFields.TIME_RANGE_WEEK;
    }
    if (hours == null || hours <= 0) {
      return UpstreamJsonFields.TIME_RANGE_MONTH;
    }
    if (hours <= 36) {
      return UpstreamJsonFields.TIME_RANGE_DAY;
    }
    if (hours <= 240) {
      return UpstreamJsonFields.TIME_RANGE_WEEK;
    }
    return UpstreamJsonFields.TIME_RANGE_MONTH;
  }

  private static String capQuery(String query) {
    if (query.length() <= UpstreamJsonFields.TAVILY_MAX_QUERY_CHARS) {
      return query;
    }
    return query.substring(0, UpstreamJsonFields.TAVILY_MAX_QUERY_CHARS);
  }

  private static List<String> copyList(List<String> source) {
    return source == null ? new ArrayList<>() : new ArrayList<>(source);
  }

  private static void addHosts(Set<String> target, List<String> hosts) {
    if (hosts == null) {
      return;
    }
    for (String host : hosts) {
      if (StrUtil.isNotBlank(host)) {
        target.add(host.trim().toLowerCase());
      }
    }
  }
}
