package com.chengwenfang.mode.hotnews.application.hotnews;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.content.facade.constant.HotNewsThemeSeedChannelKeys;
import com.chengwenfang.crawler.facade.constant.ChannelFetchDefaults;
import com.chengwenfang.crawler.facade.dto.request.ChannelFetchConfig;
import com.chengwenfang.crawler.facade.enums.ChannelType;
import com.chengwenfang.sourcing.facade.dto.request.HotNewsChannelCreateRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * 热点主题初始搜索渠道创建请求组装（启用与配置保存共用）。
 */
public final class HotNewsThemeSeedChannelFactory {
  private HotNewsThemeSeedChannelFactory() {}

  /**
   * 按渠道类型组装创建请求。
   *
   * @param typeKey     渠道类型键
   * @param queries     搜索关键词
   * @param githubQuery GitHub 查询串
   * @return 创建请求；无法创建时 null
   */
  public static HotNewsChannelCreateRequest buildChannelRequest(
      String typeKey, List<String> queries, String githubQuery) {
    if (HotNewsThemeSeedChannelKeys.SEARXNG.equals(typeKey)) {
      return HotNewsChannelCreateRequest.builder()
          .name(HotNewsThemeSeedChannelKeys.SEARXNG_DISPLAY_NAME)
          .type(ChannelType.SEARXNG.getValue())
          .baseUrl(ChannelFetchDefaults.SEARXNG_DEFAULT_INSTANCE_URL)
          .config(ChannelFetchConfig.builder()
              .queries(new ArrayList<>(queries == null ? List.of() : queries))
              .maxResults(ChannelFetchDefaults.SEARXNG_DEFAULT_MAX_RESULTS)
              .build())
          .build();
    }
    if (HotNewsThemeSeedChannelKeys.TAVILY.equals(typeKey)) {
      return HotNewsChannelCreateRequest.builder()
          .name(HotNewsThemeSeedChannelKeys.TAVILY_DISPLAY_NAME)
          .type(ChannelType.TAVILY.getValue())
          .baseUrl(ChannelFetchDefaults.TAVILY_SEARCH_API_URL)
          .config(ChannelFetchConfig.builder()
              .queries(new ArrayList<>(queries == null ? List.of() : queries))
              .maxResults(ChannelFetchDefaults.TAVILY_DEFAULT_MAX_RESULTS)
              .topic("news")
              .searchDepth("basic")
              .build())
          .build();
    }
    if (HotNewsThemeSeedChannelKeys.GITHUB.equals(typeKey) && StrUtil.isNotBlank(githubQuery)) {
      return HotNewsChannelCreateRequest.builder()
          .name(HotNewsThemeSeedChannelKeys.GITHUB_DISPLAY_NAME)
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
}
