package com.workbench.app.golden.scenario;

import cn.hutool.core.util.StrUtil;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.workbench.app.golden.GoldenScenarioContext;
import com.workbench.app.golden.driver.ApiV1ScenarioDriver;
import com.workbench.app.golden.stub.ExternalSiteStubs;
import com.workbench.crawler.facade.enums.ChannelType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 热点黄金场景共用：指桩与主题问卷样例。
 */
public final class HotNewsGoldenSupport {
  private HotNewsGoldenSupport() {}

  /**
   * 重置并指向 AI / 外部桩。
   *
   * @param api     驱动
   * @param context 上下文
   */
  public static void prepareHotNewsPipeline(ApiV1ScenarioDriver api, GoldenScenarioContext context) {
    api.reset(context);
    try {
      api.pointAiProviders();
    } catch (Exception exception) {
      throw new IllegalStateException("热点场景准备失败", exception);
    }
  }

  /**
   * 创建并提交热点主题（不含 AI 建议），返回 themeId。
   *
   * @param api  驱动
   * @param code 主题 code
   * @param name 主题名
   * @return themeId
   */
  public static String createCommittedTheme(ApiV1ScenarioDriver api, String code, String name) {
    try {
      Map<String, Object> draft =
          api.postJson(
              "/api/modes/hot_news/themes/drafts",
              Map.of("name", name, "description", name + " 基线", "code", code));
      String themeId = String.valueOf(draft.get("id"));
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("name", name);
      patch.put("description", name + " 基线");
      patch.put("briefing", sampleBriefing(name, name + " 基线"));
      api.patchJson("/api/themes/" + themeId + "/briefing", patch);
      api.postJson("/api/themes/" + themeId + "/hot-news:commit", null);
      return themeId;
    } catch (RuntimeException runtime) {
      throw runtime;
    } catch (Exception exception) {
      throw new IllegalStateException("创建热点主题失败", exception);
    }
  }

  /**
   * 将已提交主题的渠道 baseUrl 指到外部 WireMock，并补一条 RSS。
   *
   * @param api     驱动
   * @param themeId 主题
   */
  public static void pointChannelsAtExternalStubs(ApiV1ScenarioDriver api, String themeId) {
    WireMockServer external = api.externalServer();
    if (external == null) {
      throw new IllegalStateException("外部 WireMock 未绑定");
    }
    String base = external.baseUrl();
    try {
      List<Map<String, Object>> channels =
          api.getJsonList("/api/themes/" + themeId + "/hot-news/channels");
      for (Map<String, Object> channel : channels) {
        String id = String.valueOf(channel.get("id"));
        ChannelType type = ChannelType.from(String.valueOf(channel.get("type")));
        Map<String, Object> patch = new LinkedHashMap<>();
        if (type == ChannelType.SEARXNG) {
          patch.put("baseUrl", base + "/golden/searxng");
        } else if (type == ChannelType.GITHUB) {
          patch.put("baseUrl", base + ExternalSiteStubs.PATH_GITHUB);
        } else if (type == ChannelType.TAVILY) {
          patch.put("baseUrl", base + ExternalSiteStubs.PATH_TAVILY);
        } else if (type == ChannelType.RSS) {
          patch.put("baseUrl", base + ExternalSiteStubs.PATH_RSS);
        }
        if (!patch.isEmpty()) {
          api.patchJson("/api/hot-news/channels/" + id, patch);
        }
      }
      boolean hasRss =
          channels.stream()
              .anyMatch(channel -> ChannelType.from(String.valueOf(channel.get("type"))) == ChannelType.RSS);
      if (!hasRss) {
        api.putJson(
            "/api/hot-news/channels",
            Map.of(
                "id", UUID.randomUUID().toString(),
                "themeId", themeId,
                "name", "Golden RSS",
                "type", ChannelType.RSS.getValue(),
                "baseUrl", base + ExternalSiteStubs.PATH_RSS,
                "enabled", true));
      }
    } catch (RuntimeException runtime) {
      throw runtime;
    } catch (Exception exception) {
      throw new IllegalStateException("指桩热点渠道失败", exception);
    }
  }

  /**
   * 可 commit 的热点主题问卷（含评分量表）。
   *
   * @param name        主题名
   * @param description 说明
   * @return briefing
   */
  public static Map<String, Object> sampleBriefing(String name, String description) {
    Map<String, Object> briefing = new LinkedHashMap<>();
    briefing.put("schema_version", 1);
    briefing.put("step", 4);
    Map<String, Object> naming = new LinkedHashMap<>();
    naming.put("name", name);
    naming.put("description", description);
    naming.put("domain", "health_sleep");
    naming.put("domain_custom", "");
    briefing.put("naming", naming);
    Map<String, Object> radar = new LinkedHashMap<>();
    // 恰好 3 条：与 SearXNG 并发采集对齐，避免第 4 条英词在 external.jsonl 中随机出现/消失
    radar.put("search_queries", List.of("睡眠机制", "褪黑素误区", "sleep melatonin"));
    radar.put("github_query", "sleep OR melatonin");
    radar.put("seed_channels", List.of("searxng", "github"));
    briefing.put("radar", radar);
    Map<String, Object> scoring = new LinkedHashMap<>();
    scoring.put("version", 1);
    List<Map<String, Object>> dimensions = new ArrayList<>();
    dimensions.add(dim("theme_fit", "主题契合", "是否贴合主题读者", 40));
    dimensions.add(dim("depth", "可挖深度", "是否值得深写", 35));
    dimensions.add(dim("source_trust", "来源可信", "一手与权威", 25));
    scoring.put("dimensions", dimensions);
    scoring.put("scoreGuidance", "依据来源域名判断可信度");
    briefing.put("scoring_rubric", scoring);
    return briefing;
  }

  private static Map<String, Object> dim(
      String key, String label, String description, int weight) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("key", key);
    row.put("label", label);
    row.put("description", description);
    row.put("weight", weight);
    return row;
  }

  /**
   * 提交主题 → 指桩 → 雷达采集成功 → 中文提炼，返回 themeId。
   *
   * @param api  驱动
   * @param code 主题 code
   * @param name 主题名
   * @return themeId
   */
  public static String prepareThemeWithRadarAndEditorial(
      ApiV1ScenarioDriver api, String code, String name) {
    try {
      String themeId = createCommittedTheme(api, code, name);
      pointChannelsAtExternalStubs(api, themeId);
      Map<String, Object> started =
          api.postJson(
              "/api/themes/" + themeId + "/hot-news/fetch-runs",
              Map.of("hours", 72));
      String jobId = ApiV1ScenarioDriver.extractJobId(started);
      Map<String, Object> terminal = api.awaitTerminal(jobId, 300);
      if (!"succeeded".equals(String.valueOf(terminal.get("status")))) {
        throw new IllegalStateException("雷达采集失败: " + terminal);
      }
      api.postJson("/api/themes/" + themeId + "/hot-news/topics/editorialize", null);
      return themeId;
    } catch (RuntimeException runtime) {
      throw runtime;
    } catch (Exception exception) {
      throw new IllegalStateException("热点雷达+提炼准备失败", exception);
    }
  }

  /**
   * 选取首个非 AI 种子选题 ID（雷达英文源经提炼后的选题）。
   *
   * @param api     驱动
   * @param themeId 主题
   * @return topicId
   */
  public static String firstRadarTopicId(ApiV1ScenarioDriver api, String themeId) {
    try {
      List<Map<String, Object>> topics =
          api.getJsonList("/api/themes/" + themeId + "/hot-news/topics");
      for (Map<String, Object> topic : topics) {
        if (topic == null) {
          continue;
        }
        Object origin = topic.get("originKind");
        if (origin != null && String.valueOf(origin).contains("ai_theme_seed")) {
          continue;
        }
        Object id = topic.get("id");
        if (id != null && StrUtil.isNotBlank(String.valueOf(id))) {
          return String.valueOf(id);
        }
      }
      throw new IllegalStateException("未找到雷达选题 themeId=" + themeId + " topics=" + topics);
    } catch (RuntimeException runtime) {
      throw runtime;
    } catch (Exception exception) {
      throw new IllegalStateException("选取雷达选题失败", exception);
    }
  }

  /**
   * 精简信源渠道列表。
   *
   * @param channels 原始
   * @return 稳定字段
   */
  public static List<Map<String, Object>> slimChannels(List<Map<String, Object>> channels) {
    List<Map<String, Object>> out = new ArrayList<>();
    if (channels == null) {
      return out;
    }
    for (Map<String, Object> channel : channels) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("name", channel.get("name"));
      row.put("type", channel.get("type"));
      row.put("baseUrl", channel.get("baseUrl"));
      row.put("enabled", channel.get("enabled"));
      out.add(row);
    }
    return out;
  }
}
