package com.chengwenfang.mode.hotnews.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.ai.facade.AiTextFacade;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.dto.request.TextCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.TextCompleteResult;
import com.chengwenfang.content.facade.ContentFacade;
import com.chengwenfang.content.facade.dto.view.ContentThemeView;
import com.chengwenfang.crawler.facade.enums.ChannelType;
import com.chengwenfang.mode.hotnews.application.prompt.ResearchSourcePolicyPrompts;
import com.chengwenfang.mode.hotnews.application.research.ResearchSourcePolicy;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsResearchSourceDefaults;
import com.chengwenfang.sourcing.domain.entity.SourceChannelEntity;
import com.chengwenfang.sourcing.infrastructure.mapper.SourceChannelMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 资料补充来源策略：补搜时 AI 现生成，不落库；渠道域名仅作提示合并。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResearchSourcePolicyResolver {

  /** AI 文本门面。 */
  private final AiTextFacade ai;
  /** 内容主题门面。 */
  private final ContentFacade content;
  /** 渠道 Mapper。 */
  private final SourceChannelMapper channelMapper;

  /**
   * 按本次选题现生成来源策略（不写库）。
   *
   * @param themeId      主题 ID
   * @param topicTitle   选题标题
   * @param topicSummary 选题摘要
   * @return 策略（永不为 null）
   */
  public ResearchSourcePolicy resolve(String themeId, String topicTitle, String topicSummary) {
    String themeName = themeName(themeId);
    ResearchSourcePolicy fromAi = generateByAi(themeName, topicTitle, topicSummary);
    Set<String> preferred = new LinkedHashSet<>();
    Set<String> blocked = new LinkedHashSet<>();
    Set<String> angles = new LinkedHashSet<>();
    String packKey = null;
    boolean siteSearch = true;
    boolean openSearch = true;

    if (fromAi != null) {
      packKey = fromAi.getPackKey();
      addHosts(preferred, fromAi.getPreferredDomains());
      addHosts(blocked, fromAi.getBlockedDomains());
      if (fromAi.getQueryAngles() != null) {
        for (String angle : fromAi.getQueryAngles()) {
          if (StrUtil.isNotBlank(angle)) {
            angles.add(angle.trim());
          }
        }
      }
      siteSearch = fromAi.isSiteSearchEnabled();
      openSearch = fromAi.isOpenSearchEnabled();
    }

    // 主题已配 web/rss 渠道域名作补充提示，不替代 AI 规划
    addHosts(preferred, hostsFromThemeChannels(themeId));
    // 默认屏蔽域始终并入，避免模型漏写 bilibili 又被开放搜占满
    addHosts(blocked, HotNewsResearchSourceDefaults.DEFAULT_BLOCKED_DOMAINS);
    // AI 失败且仍无优选域时，按主题名兜底金融包
    if (preferred.isEmpty() && looksFinanceTheme(themeName, packKey)) {
      packKey = StrUtil.blankToDefault(packKey, "finance");
      addHosts(preferred, HotNewsResearchSourceDefaults.FINANCE_PREFERRED_DOMAINS);
      if (angles.isEmpty()) {
        angles.addAll(HotNewsResearchSourceDefaults.FINANCE_QUERY_ANGLES);
      }
    }

    ResearchSourcePolicy policy = ResearchSourcePolicy.builder()
        .packKey(packKey)
        .preferredDomains(new ArrayList<>(preferred))
        .blockedDomains(new ArrayList<>(blocked))
        .queryAngles(new ArrayList<>(angles))
        .siteSearchEnabled(siteSearch)
        .openSearchEnabled(openSearch)
        .build();
    log.info(
        "资料补充来源策略(现生成) themeId={} pack={} preferred={} blocked={} angles={} fromAi={}",
        themeId,
        policy.getPackKey(),
        policy.getPreferredDomains(),
        policy.getBlockedDomains(),
        policy.getQueryAngles(),
        fromAi != null);
    return policy;
  }

  /**
   * 由选题主体与策略展开 site: 查询（有上限）。
   *
   * @param subject 主体
   * @param policy  策略
   * @return 查询列表
   */
  public static List<String> buildSiteScopedQueries(String subject, ResearchSourcePolicy policy) {
    if (policy == null || !policy.isSiteSearchEnabled() || !policy.hasPreferredDomains()) {
      return List.of();
    }
    String primary = StrUtil.trim(subject);
    if (StrUtil.isBlank(primary)) {
      return List.of();
    }
    List<String> angles = policy.getQueryAngles() == null || policy.getQueryAngles().isEmpty()
        ? List.of("")
        : policy.getQueryAngles();
    LinkedHashSet<String> queries = new LinkedHashSet<>();
    for (String domain : policy.getPreferredDomains()) {
      if (StrUtil.isBlank(domain)) {
        continue;
      }
      String host = normalizeHost(domain);
      if (StrUtil.isBlank(host)) {
        continue;
      }
      for (String angle : angles) {
        String query = StrUtil.isBlank(angle)
            ? primary + " site:" + host
            : primary + " " + angle.trim() + " site:" + host;
        queries.add(query.trim());
        if (queries.size() >= 12) {
          return new ArrayList<>(queries);
        }
      }
    }
    return new ArrayList<>(queries);
  }

  /**
   * 由选题主体与角度词展开短查询（无 site:，供 Tavily include_domains 场景）。
   *
   * @param subject 主体
   * @param policy  策略
   * @return 查询列表（最多 5 条）
   */
  public static List<String> buildAngleQueries(String subject, ResearchSourcePolicy policy) {
    String primary = StrUtil.trim(subject);
    if (StrUtil.isBlank(primary)) {
      return List.of();
    }
    LinkedHashSet<String> queries = new LinkedHashSet<>();
    queries.add(primary);
    if (policy != null && policy.getQueryAngles() != null) {
      for (String angle : policy.getQueryAngles()) {
        if (StrUtil.isBlank(angle)) {
          continue;
        }
        queries.add((primary + " " + angle.trim()).trim());
        if (queries.size() >= 5) {
          break;
        }
      }
    }
    return new ArrayList<>(queries);
  }

  /**
   * URL 是否命中屏蔽域名。
   *
   * @param url    链接
   * @param policy 策略
   * @return 应跳过时 true
   */
  public static boolean isBlockedUrl(String url, ResearchSourcePolicy policy) {
    if (policy == null || CollUtil.isEmpty(policy.getBlockedDomains()) || StrUtil.isBlank(url)) {
      return false;
    }
    String host = hostOf(url);
    if (StrUtil.isBlank(host)) {
      return false;
    }
    for (String blocked : policy.getBlockedDomains()) {
      String needle = normalizeHost(blocked);
      if (StrUtil.isBlank(needle)) {
        continue;
      }
      if (host.equals(needle) || host.endsWith("." + needle)) {
        return true;
      }
    }
    return false;
  }

  private ResearchSourcePolicy generateByAi(String themeName, String topicTitle, String topicSummary) {
    String title = StrUtil.nullToEmpty(topicTitle).trim();
    String summary = StrUtil.nullToEmpty(topicSummary).trim();
    if (StrUtil.isBlank(title) && StrUtil.isBlank(summary) && StrUtil.isBlank(themeName)) {
      return null;
    }
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("themeName", StrUtil.nullToEmpty(themeName));
      payload.put("title", title);
      payload.put(
          "summary", summary.length() > 800 ? summary.substring(0, 800) : summary);
      TextCompleteResult result = ai.completeText(TextCompleteRequest.builder()
          .scene(ModelCallScenes.HOT_NEWS_TOPIC_RESEARCH_SOURCE_POLICY)
          .systemPrompt(ResearchSourcePolicyPrompts.SYSTEM)
          .prompt(JSONUtil.toJsonStr(payload))
          .timeoutSeconds(60)
          .build());
      ResearchSourcePolicy parsed = parsePolicy(result == null ? null : result.getText());
      if (parsed != null && parsed.hasPreferredDomains()) {
        return parsed;
      }
    } catch (Exception exception) {
      log.warn("来源策略 AI 生成失败 err={}", exception.getMessage());
    }
    return null;
  }

  /**
   * 解析策略 JSON。
   *
   * @param raw 模型原文
   * @return 策略；失败 null
   */
  static ResearchSourcePolicy parsePolicy(String raw) {
    JSONObject json = extractJsonObject(raw);
    if (json == null) {
      return null;
    }
    Map<String, Object> map = new LinkedHashMap<>();
    for (String key : json.keySet()) {
      map.put(key, json.get(key));
    }
    return fromMap(map);
  }

  static ResearchSourcePolicy fromMap(Map<String, Object> source) {
    if (source == null || source.isEmpty()) {
      return null;
    }
    return ResearchSourcePolicy.builder()
        .packKey(str(source.get("packKey")))
        .preferredDomains(stringList(source.get("preferredDomains")))
        .blockedDomains(stringList(source.get("blockedDomains")))
        .queryAngles(stringList(source.get("queryAngles")))
        .siteSearchEnabled(bool(source.get("siteSearchEnabled"), true))
        .openSearchEnabled(bool(source.get("openSearchEnabled"), true))
        .build();
  }

  private List<String> hostsFromThemeChannels(String themeId) {
    if (StrUtil.isBlank(themeId)) {
      return List.of();
    }
    List<SourceChannelEntity> channels = channelMapper.selectList(new LambdaQueryWrapper<SourceChannelEntity>()
        .eq(SourceChannelEntity::getThemeId, themeId)
        .eq(SourceChannelEntity::getEnabled, true)
        .in(SourceChannelEntity::getChannelType, List.of(
            ChannelType.WEB.getValue(),
            ChannelType.RSS.getValue())));
    LinkedHashSet<String> hosts = new LinkedHashSet<>();
    if (channels == null) {
      return List.of();
    }
    for (SourceChannelEntity channel : channels) {
      if (channel == null) {
        continue;
      }
      String host = hostOf(channel.getBaseUrl());
      if (StrUtil.isNotBlank(host)
          && !host.contains("127.0.0.1")
          && !host.contains("localhost")) {
        hosts.add(host);
      }
    }
    return new ArrayList<>(hosts);
  }

  private String themeName(String themeId) {
    if (StrUtil.isBlank(themeId)) {
      return "";
    }
    try {
      ContentThemeView theme = content.getTheme(themeId);
      return theme == null ? "" : StrUtil.nullToEmpty(theme.getName());
    } catch (Exception ignored) {
      return "";
    }
  }

  private boolean looksFinanceTheme(String themeName, String packKey) {
    return HotNewsResearchSourceDefaults.looksFinanceTheme(themeName, packKey);
  }

  private static void addHosts(Set<String> target, List<String> hosts) {
    if (hosts == null) {
      return;
    }
    for (String host : hosts) {
      String normalized = normalizeHost(host);
      if (StrUtil.isNotBlank(normalized)) {
        target.add(normalized);
      }
    }
  }

  private static String normalizeHost(String raw) {
    if (StrUtil.isBlank(raw)) {
      return "";
    }
    String text = raw.trim().toLowerCase(Locale.ROOT);
    if (text.startsWith("http://") || text.startsWith("https://")) {
      return hostOf(text);
    }
    int slash = text.indexOf('/');
    if (slash > 0) {
      text = text.substring(0, slash);
    }
    if (text.startsWith("www.")) {
      text = text.substring(4);
    }
    return text;
  }

  private static String hostOf(String url) {
    if (StrUtil.isBlank(url)) {
      return "";
    }
    try {
      String raw = url.trim();
      if (!raw.contains("://")) {
        raw = "https://" + raw;
      }
      URI uri = URI.create(raw);
      String host = uri.getHost();
      if (host == null) {
        return "";
      }
      host = host.toLowerCase(Locale.ROOT);
      if (host.startsWith("www.")) {
        host = host.substring(4);
      }
      return host;
    } catch (Exception ignored) {
      return "";
    }
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

  private static String str(Object value) {
    return value == null ? null : StrUtil.trim(String.valueOf(value));
  }

  private static boolean bool(Object value, boolean defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    if (value instanceof Boolean boxed) {
      return boxed;
    }
    return Boolean.parseBoolean(String.valueOf(value));
  }

  private static List<String> stringList(Object raw) {
    List<String> out = new ArrayList<>();
    if (raw instanceof JSONArray array) {
      for (Object item : array) {
        String text = StrUtil.trim(String.valueOf(item));
        if (StrUtil.isNotBlank(text)) {
          out.add(text);
        }
      }
      return out;
    }
    if (raw instanceof List<?> list) {
      for (Object item : list) {
        String text = StrUtil.trim(String.valueOf(item));
        if (StrUtil.isNotBlank(text)) {
          out.add(text);
        }
      }
    }
    return out;
  }
}
