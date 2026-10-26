package com.workbench.mode.hotnews.application.hotnews;

import cn.hutool.core.util.StrUtil;
import com.workbench.content.facade.ContentFacade;
import com.workbench.content.facade.HotNewsThemeBriefingSupport;
import com.workbench.content.facade.constant.HotNewsThemeSeedChannelKeys;
import com.workbench.content.facade.dto.view.ContentThemeView;
import com.workbench.content.facade.dto.view.HotNewsThemeCommitView;
import com.workbench.crawler.facade.constant.ChannelFetchDefaults;
import com.workbench.crawler.facade.dto.request.ChannelFetchConfig;
import com.workbench.crawler.facade.enums.ChannelType;
import com.workbench.mode.hotnews.facade.HotNewsFacade;
import com.workbench.sourcing.facade.dto.request.HotNewsChannelCreateRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 热点主题确认启用：校验问卷、启用主题，并按 briefing 创建初始搜索渠道。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotNewsThemeCommitOrchestrator {
  /** 内容主题门面。 */
  private final ContentFacade content;
  /** 热点资讯门面。 */
  private final HotNewsFacade hotNews;

  /**
   * 确认启用热点主题并种子化初始渠道。
   *
   * @param themeId 主题 id/code
   * @return 启用结果（含创建渠道数）
   */
  @Transactional
  public HotNewsThemeCommitView commit(String themeId) {
    // 先启用主题并校验问卷
    HotNewsThemeCommitView enabled = content.commitHotNewsTheme(themeId);
    ContentThemeView theme = content.getTheme(enabled.getThemeId());
    Map<String, Object> briefing = readBriefing(theme.getBriefingJson());
    List<String> queries = HotNewsThemeBriefingSupport.searchQueries(briefing);
    String githubQuery = HotNewsThemeBriefingSupport.githubQuery(briefing);
    List<String> seedTypes = enabled.getSeededChannelTypes() == null
        ? HotNewsThemeBriefingSupport.seedChannelTypes(briefing)
        : enabled.getSeededChannelTypes();

    List<String> createdTypes = new ArrayList<>();
    for (String typeKey : seedTypes) {
      // 按确认清单创建初始搜索渠道
      HotNewsChannelCreateRequest request = buildChannelRequest(typeKey, queries, githubQuery);
      if (request == null) {
        continue;
      }
      hotNews.createChannel(theme.getId(), request);
      createdTypes.add(typeKey);
    }
    log.info("热点主题初始渠道已创建 themeId={} types={}", theme.getId(), createdTypes);
    return HotNewsThemeCommitView.builder()
        .themeId(theme.getId())
        .status(enabled.getStatus())
        .channelCount(createdTypes.size())
        .seededChannelTypes(createdTypes)
        .build();
  }

  /**
   * 按渠道类型组装创建请求。
   *
   * @param typeKey     渠道类型键
   * @param queries     搜索关键词
   * @param githubQuery GitHub 查询串
   * @return 创建请求；无法创建时 null
   */
  private HotNewsChannelCreateRequest buildChannelRequest(
      String typeKey, List<String> queries, String githubQuery) {
    if (HotNewsThemeSeedChannelKeys.SEARXNG.equals(typeKey)) {
      return HotNewsChannelCreateRequest.builder()
          .name("SearXNG 搜索")
          .type(ChannelType.SEARXNG.getValue())
          .baseUrl(ChannelFetchDefaults.SEARXNG_DEFAULT_INSTANCE_URL)
          .config(ChannelFetchConfig.builder()
              .queries(new ArrayList<>(queries))
              .maxResults(ChannelFetchDefaults.SEARXNG_DEFAULT_MAX_RESULTS)
              .build())
          .build();
    }
    if (HotNewsThemeSeedChannelKeys.TAVILY.equals(typeKey)) {
      return HotNewsChannelCreateRequest.builder()
          .name("Tavily 搜索")
          .type(ChannelType.TAVILY.getValue())
          .baseUrl(ChannelFetchDefaults.TAVILY_SEARCH_API_URL)
          .config(ChannelFetchConfig.builder()
              .queries(new ArrayList<>(queries))
              .maxResults(ChannelFetchDefaults.TAVILY_DEFAULT_MAX_RESULTS)
              .topic("news")
              .searchDepth("basic")
              .build())
          .build();
    }
    if (HotNewsThemeSeedChannelKeys.GITHUB.equals(typeKey) && StrUtil.isNotBlank(githubQuery)) {
      return HotNewsChannelCreateRequest.builder()
          .name("GitHub 搜索")
          .type(ChannelType.GITHUB.getValue())
          .baseUrl(ChannelFetchDefaults.GITHUB_SEARCH_API_URL)
          .config(ChannelFetchConfig.builder()
              .query(githubQuery.trim())
              .dateField("updated")
              .sort("stars")
              .build())
          .build();
    }
    return null;
  }

  /**
   * 解析 briefing JSON。
   *
   * @param json briefing 字符串
   * @return map；解析失败返回空 map
   */
  private Map<String, Object> readBriefing(String json) {
    if (StrUtil.isBlank(json)) {
      return Map.of();
    }
    try {
      Object parsed = cn.hutool.json.JSONUtil.parse(json);
      if (parsed instanceof Map<?, ?> map) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
          if (entry.getKey() != null) {
            out.put(String.valueOf(entry.getKey()), entry.getValue());
          }
        }
        return out;
      }
    } catch (Exception ignored) {
      // 问卷解析失败时由启用步骤已校验，此处回退空 map
    }
    return Map.of();
  }
}
