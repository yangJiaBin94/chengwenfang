package com.chengwenfang.mode.hotnews.application.hotnews;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.common.SearchQueryNormalizer;
import com.chengwenfang.crawler.facade.FetchFacade;
import com.chengwenfang.crawler.facade.constant.ChannelFetchDefaults;
import com.chengwenfang.crawler.facade.dto.request.ChannelFetchConfig;
import com.chengwenfang.crawler.facade.dto.request.FetchRequest;
import com.chengwenfang.crawler.facade.dto.view.FetchResult;
import com.chengwenfang.crawler.facade.enums.ChannelType;
import com.chengwenfang.mode.hotnews.facade.HotNewsFacade;
import com.chengwenfang.sourcing.facade.HotNewsChannelQuotaSupport;
import com.chengwenfang.sourcing.facade.dto.request.HotNewsChannelCreateRequest;
import com.chengwenfang.sourcing.facade.dto.view.HotNewsChannelView;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 渠道建议校验：规范化、连通性探测与去重。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelSuggestionValidator {

  /** 热点门面（创建渠道）。 */
  private final HotNewsFacade hotNews;
  /** 抓取门面（连通性探测）。 */
  private final FetchFacade fetchFacade;

  ChannelSuggestionProcessResult processCandidate(
      String themeId,
      ChannelSuggestionParser.AiChannelCandidate candidate,
      ChannelSuggestionExistingIndex existingIndex,
      Set<String> acceptedKeys,
      List<String> searchQueries,
      String githubQuery) {
    if (candidate == null) {
      return ChannelSuggestionProcessResult.skipped("", "", "", HotNewsSourceChannelSkipReasons.INVALID_FIELDS);
    }
    String name = StrUtil.trim(StrUtil.nullToEmpty(candidate.name()));
    ChannelType type = ChannelType.from(candidate.type());
    if (type == null) {
      return ChannelSuggestionProcessResult.skipped(
          name, StrUtil.nullToEmpty(candidate.type()), StrUtil.nullToEmpty(candidate.baseUrl()),
          HotNewsSourceChannelSkipReasons.INVALID_TYPE);
    }
    if (StrUtil.isBlank(name)) {
      return ChannelSuggestionProcessResult.skipped(
          name, type.getValue(), StrUtil.nullToEmpty(candidate.baseUrl()),
          HotNewsSourceChannelSkipReasons.INVALID_FIELDS);
    }

    if (type == ChannelType.SEARXNG && existingIndex.hasSearxng()) {
      return ChannelSuggestionProcessResult.skipped(
          name, type.getValue(), ChannelFetchDefaults.SEARXNG_DEFAULT_INSTANCE_URL,
          HotNewsSourceChannelSkipReasons.SEARXNG_EXISTS);
    }

    HotNewsChannelCreateRequest request = normalizeCreateRequest(candidate, type, searchQueries, githubQuery);
    if (request == null) {
      String reason = switch (type) {
        case SEARXNG, TAVILY -> HotNewsSourceChannelSkipReasons.MISSING_QUERIES;
        case GITHUB -> HotNewsSourceChannelSkipReasons.MISSING_GITHUB_QUERY;
        default -> HotNewsSourceChannelSkipReasons.INVALID_FIELDS;
      };
      return ChannelSuggestionProcessResult.skipped(
          name, type.getValue(), StrUtil.nullToEmpty(candidate.baseUrl()), reason);
    }

    // 网页：按 URL 探测后裁剪
    if (type == ChannelType.WEB) {
      return processWebCandidate(themeId, request, acceptedKeys, existingIndex);
    }

    String dedupeKey = dedupeKey(type.getValue(), request.getBaseUrl());
    if (acceptedKeys.contains(dedupeKey) || existingIndex.dedupeKeys().contains(dedupeKey)) {
      return ChannelSuggestionProcessResult.skipped(
          request.getName(), type.getValue(), request.getBaseUrl(),
          HotNewsSourceChannelSkipReasons.DUPLICATE);
    }

    if (!testWholeChannel(request)) {
      return ChannelSuggestionProcessResult.skipped(
          request.getName(), type.getValue(), request.getBaseUrl(),
          HotNewsSourceChannelSkipReasons.CONNECTIVITY_FAILED);
    }

    HotNewsChannelView created = hotNews.createChannel(themeId, request);
    return ChannelSuggestionProcessResult.created(created, Set.of(dedupeKey));
  }

  /**
   * 网页候选：逐 URL 校验，只落可用地址。
   *
   * @param themeId       主题 id
   * @param request       规范化后的创建请求
   * @param acceptedKeys  本轮已占用键
   * @param existingIndex 已有索引
   * @return 处理结果
   */
  private ChannelSuggestionProcessResult processWebCandidate(
      String themeId,
      HotNewsChannelCreateRequest request,
      Set<String> acceptedKeys,
      ChannelSuggestionExistingIndex existingIndex) {
    // 先列表内去重并截断，再剔除主题已配置网页 URL
    List<String> rawUrls = dedupeAndCapWebUrls(resolveWebUrls(request));
    List<String> okUrls = new ArrayList<>();
    int duplicateCount = 0;
    int failedCount = 0;
    for (String url : rawUrls) {
      String key = dedupeKey(ChannelType.WEB.getValue(), url);
      if (acceptedKeys.contains(key) || existingIndex.webUrls().contains(normalizeUrl(url))) {
        duplicateCount++;
        continue;
      }
      if (testSingleWebUrl(url)) {
        okUrls.add(url);
      } else {
        failedCount++;
      }
    }
    if (okUrls.isEmpty()) {
      String reason = failedCount == 0 && duplicateCount > 0
          ? HotNewsSourceChannelSkipReasons.DUPLICATE
          : HotNewsSourceChannelSkipReasons.NO_VALID_URL;
      return ChannelSuggestionProcessResult.skipped(
          request.getName(),
          ChannelType.WEB.getValue(),
          request.getBaseUrl(),
          reason);
    }
    ChannelFetchConfig config = request.getConfig() == null
        ? ChannelFetchConfig.builder().build()
        : request.getConfig();
    config.setUrls(new ArrayList<>(okUrls));
    HotNewsChannelCreateRequest filtered = HotNewsChannelCreateRequest.builder()
        .name(request.getName())
        .type(ChannelType.WEB.getValue())
        .baseUrl(okUrls.get(0))
        .config(config)
        .build();
    HotNewsChannelView created = hotNews.createChannel(themeId, filtered);
    Set<String> keys = new LinkedHashSet<>();
    for (String url : okUrls) {
      keys.add(dedupeKey(ChannelType.WEB.getValue(), url));
    }
    return ChannelSuggestionProcessResult.created(created, keys);
  }

  /**
   * 规范化 AI 候选为创建请求（补默认 baseUrl / 查询词）。
   *
   * @param candidate     AI 候选
   * @param type          已解析类型
   * @param searchQueries 主题搜索词
   * @param githubQuery   主题 GitHub 查询
   * @return 创建请求；无法规范化时 null
   */
  HotNewsChannelCreateRequest normalizeCreateRequest(
      ChannelSuggestionParser.AiChannelCandidate candidate,
      ChannelType type,
      List<String> searchQueries,
      String githubQuery) {
    String name = StrUtil.trim(StrUtil.nullToEmpty(candidate.name()));
    ChannelFetchConfig config = candidate.config() == null
        ? ChannelFetchConfig.builder().build()
        : candidate.config();

    if (type == ChannelType.SEARXNG) {
      List<String> queries = config.resolveQueries();
      if (queries.isEmpty()) {
        queries = searchQueries == null ? List.of() : searchQueries;
      }
      queries = SearchQueryNormalizer.normalize(queries);
      if (queries.isEmpty()) {
        return null;
      }
      if (config.getMaxResults() == null || config.getMaxResults() <= 0) {
        config.setMaxResults(ChannelFetchDefaults.SEARXNG_DEFAULT_MAX_RESULTS);
      }
      config.setQueries(new ArrayList<>(queries));
      return HotNewsChannelCreateRequest.builder()
          .name(name)
          .type(type.getValue())
          .baseUrl(ChannelFetchDefaults.SEARXNG_DEFAULT_INSTANCE_URL)
          .config(config)
          .build();
    }

    if (type == ChannelType.TAVILY) {
      List<String> queries = config.resolveQueries();
      if (queries.isEmpty()) {
        queries = searchQueries == null ? List.of() : searchQueries;
      }
      queries = SearchQueryNormalizer.normalize(queries);
      if (queries.isEmpty()) {
        return null;
      }
      if (config.getMaxResults() == null || config.getMaxResults() <= 0) {
        config.setMaxResults(ChannelFetchDefaults.TAVILY_DEFAULT_MAX_RESULTS);
      }
      if (StrUtil.isBlank(config.getTopic())) {
        config.setTopic("news");
      }
      if (StrUtil.isBlank(config.getSearchDepth())) {
        config.setSearchDepth("basic");
      }
      config.setQueries(new ArrayList<>(queries));
      return HotNewsChannelCreateRequest.builder()
          .name(name)
          .type(type.getValue())
          .baseUrl(ChannelFetchDefaults.TAVILY_SEARCH_API_URL)
          .config(config)
          .build();
    }

    if (type == ChannelType.GITHUB) {
      String query = StrUtil.trim(StrUtil.nullToEmpty(config.getQuery()));
      if (StrUtil.isBlank(query)) {
        query = StrUtil.trim(StrUtil.nullToEmpty(githubQuery));
      }
      if (StrUtil.isBlank(query)) {
        return null;
      }
      config.setQuery(query);
      if (StrUtil.isBlank(config.getDateField())) {
        config.setDateField("updated");
      }
      if (StrUtil.isBlank(config.getSort())) {
        config.setSort("stars");
      }
      return HotNewsChannelCreateRequest.builder()
          .name(name)
          .type(type.getValue())
          .baseUrl(ChannelFetchDefaults.GITHUB_SEARCH_API_URL)
          .config(config)
          .build();
    }

    if (type == ChannelType.WEB) {
      List<String> urls = new ArrayList<>(config.resolveUrls());
      String baseUrl = StrUtil.trim(StrUtil.nullToEmpty(candidate.baseUrl()));
      if (urls.isEmpty() && StrUtil.isNotBlank(baseUrl)) {
        urls.add(baseUrl);
      }
      urls = dedupeAndCapWebUrls(urls);
      if (urls.isEmpty()) {
        return null;
      }
      config.setUrls(urls);
      return HotNewsChannelCreateRequest.builder()
          .name(name)
          .type(type.getValue())
          .baseUrl(urls.get(0))
          .config(config)
          .build();
    }

    // RSS
    String baseUrl = StrUtil.trim(StrUtil.nullToEmpty(candidate.baseUrl()));
    if (StrUtil.isBlank(baseUrl)) {
      return null;
    }
    return HotNewsChannelCreateRequest.builder()
        .name(name)
        .type(type.getValue())
        .baseUrl(baseUrl)
        .config(config == null ? ChannelFetchConfig.builder().build() : config)
        .build();
  }

  /**
   * 整渠连通性探测（rss / searxng / github / tavily）。
   *
   * @param request 创建请求
   * @return 是否可用
   */
  boolean testWholeChannel(HotNewsChannelCreateRequest request) {
    try {
      FetchResult result = fetchFacade.testConnection(FetchRequest.builder()
          .url(request.getBaseUrl())
          .type(request.getType())
          .timeoutSeconds(ChannelFetchDefaults.TEST_TIMEOUT_SECONDS)
          .hours(ChannelFetchDefaults.TEST_HOURS)
          .config(request.getConfig())
          .build());
      return result != null && result.isOk();
    } catch (Exception exception) {
      log.warn("来源连通性探测异常 type={} url={} err={}",
          request.getType(), request.getBaseUrl(), exception.getMessage());
      return false;
    }
  }

  /**
   * 单网页 URL 连通性探测。
   *
   * @param url 网页地址
   * @return 是否可用
   */
  boolean testSingleWebUrl(String url) {
    try {
      FetchResult result = fetchFacade.testConnection(FetchRequest.builder()
          .url(url)
          .type(ChannelType.WEB.getValue())
          .timeoutSeconds(ChannelFetchDefaults.TEST_TIMEOUT_SECONDS)
          .hours(ChannelFetchDefaults.TEST_HOURS)
          .config(ChannelFetchConfig.builder().urls(List.of(url)).build())
          .build());
      return result != null && result.isOk();
    } catch (Exception exception) {
      log.warn("网页地址连通性探测异常 url={} err={}", url, exception.getMessage());
      return false;
    }
  }

  static List<String> resolveWebUrls(HotNewsChannelCreateRequest request) {
    List<String> urls = new ArrayList<>();
    if (request.getConfig() != null) {
      for (String url : request.getConfig().resolveUrls()) {
        if (StrUtil.isNotBlank(url)) {
          urls.add(url.trim());
        }
      }
    }
    if (urls.isEmpty() && StrUtil.isNotBlank(request.getBaseUrl())) {
      urls.add(request.getBaseUrl().trim());
    }
    return urls;
  }

  /**
   * 去重键：type + 规范化 URL。
   *
   * @param type 类型
   * @param url  地址
   * @return 键
   */
  static String dedupeKey(String type, String url) {
    return StrUtil.nullToEmpty(type).toLowerCase(Locale.ROOT)
        + "|"
        + normalizeUrl(url);
  }

  /**
   * URL 规范化：去尾斜杠、小写 host 段粗略处理。
   *
   * @param url 原始 URL
   * @return 规范化串
   */
  static String normalizeUrl(String url) {
    String text = StrUtil.trim(StrUtil.nullToEmpty(url)).toLowerCase(Locale.ROOT);
    while (text.endsWith("/") && text.length() > 1) {
      text = text.substring(0, text.length() - 1);
    }
    return text;
  }

  /**
   * 网页 URL 列表内去重（按规范化键）并截断到 {@link HotNewsThemeSourceChannelGenerateService#MAX_WEB_URLS}。
   *
   * @param urls 原始 URL 列表
   * @return 去重截断后的列表（保序）
   */
  static List<String> dedupeAndCapWebUrls(List<String> urls) {
    List<String> deduped = HotNewsChannelQuotaSupport.dedupeUrls(urls);
    if (deduped.size() <= HotNewsThemeSourceChannelGenerateService.MAX_WEB_URLS) {
      return deduped;
    }
    return new ArrayList<>(deduped.subList(0, HotNewsThemeSourceChannelGenerateService.MAX_WEB_URLS));
  }
}
