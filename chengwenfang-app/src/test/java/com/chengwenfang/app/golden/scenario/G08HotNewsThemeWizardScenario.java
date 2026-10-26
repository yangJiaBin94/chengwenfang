package com.chengwenfang.app.golden.scenario;

import com.chengwenfang.app.golden.GoldenScenario;
import com.chengwenfang.app.golden.GoldenScenarioContext;
import com.chengwenfang.app.golden.driver.ApiV1ScenarioDriver;
import com.chengwenfang.app.golden.driver.ScenarioDriver;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * G08：热点主题向导（建议搜索词 / GitHub / 量表 → 提交 → AI 信源）。
 */
public final class G08HotNewsThemeWizardScenario implements GoldenScenario {
  /** 场景 ID。 */
  public static final String ID = "G08";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public void prepare(ScenarioDriver driver, GoldenScenarioContext context) {
    if (!(driver instanceof ApiV1ScenarioDriver api)) {
      throw new IllegalStateException("G08 需要 ApiV1ScenarioDriver");
    }
    HotNewsGoldenSupport.prepareHotNewsPipeline(api, context);
  }

  @Override
  public void execute(ScenarioDriver driver, GoldenScenarioContext context) {
    ApiV1ScenarioDriver api = (ApiV1ScenarioDriver) driver;
    try {
      Map<String, Object> draft =
          api.postJson(
              "/api/modes/hot_news/themes/drafts",
              Map.of(
                  "name", "黄金睡眠热点",
                  "description", "G08 热点主题向导",
                  "code", "g08_golden_theme"));
      String themeId = String.valueOf(draft.get("id"));
      context.put("themeId", themeId);

      Map<String, Object> searchSuggest =
          api.postJson(
              "/api/themes/hot-news:suggest-search-queries",
              Map.of(
                  "name", "黄金睡眠热点",
                  "description", "G08 热点主题向导",
                  "domainLabel", "健康睡眠"));
      context.put("searchSuggest", searchSuggest);

      Map<String, Object> githubSuggest =
          api.postJson(
              "/api/themes/hot-news:suggest-github-query",
              Map.of(
                  "name", "黄金睡眠热点",
                  "description", "G08 热点主题向导",
                  "domainLabel", "健康睡眠",
                  "searchQueries", List.of("睡眠机制", "褪黑素误区")));
      context.put("githubSuggest", githubSuggest);

      Map<String, Object> rubricSuggest =
          api.postJson(
              "/api/themes/hot-news:suggest-scoring-rubric",
              Map.of(
                  "name", "黄金睡眠热点",
                  "description", "G08 热点主题向导",
                  "domainLabel", "健康睡眠",
                  "searchQueries", List.of("睡眠机制", "褪黑素误区")));
      context.put("rubricSuggest", rubricSuggest);

      Map<String, Object> briefing =
          HotNewsGoldenSupport.sampleBriefing("黄金睡眠热点", "G08 热点主题向导");
      // 用 AI 建议覆盖问卷中的搜索词 / GitHub / 量表（若 AI 未命中则保留样例）
      Object queries = searchSuggest.get("queries");
      if (queries instanceof List<?> list && !list.isEmpty()) {
        @SuppressWarnings("unchecked")
        Map<String, Object> radar = (Map<String, Object>) briefing.get("radar");
        radar.put("search_queries", queries);
      }
      Object githubQuery = githubSuggest.get("query");
      if (githubQuery != null && !String.valueOf(githubQuery).isBlank()) {
        @SuppressWarnings("unchecked")
        Map<String, Object> radar = (Map<String, Object>) briefing.get("radar");
        radar.put("github_query", githubQuery);
      }
      Object rubric = rubricSuggest.get("rubric");
      if (rubric instanceof Map<?, ?>) {
        briefing.put("scoring_rubric", rubric);
      }

      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("name", "黄金睡眠热点");
      patch.put("description", "G08 热点主题向导");
      patch.put("briefing", briefing);
      api.patchJson("/api/themes/" + themeId + "/briefing", patch);

      Map<String, Object> commit = api.postJson("/api/themes/" + themeId + "/hot-news:commit", null);
      context.put("commit", commit);

      Map<String, Object> generated =
          api.postJson("/api/themes/" + themeId + "/hot-news/channels:generate-ai", null);
      context.put("generatedChannels", generated);
    } catch (RuntimeException runtime) {
      throw runtime;
    } catch (Exception exception) {
      throw new IllegalStateException("G08 执行失败", exception);
    }
  }

  @Override
  public Map<String, String> collect(ScenarioDriver driver, GoldenScenarioContext context) {
    ApiV1ScenarioDriver api = (ApiV1ScenarioDriver) driver;
    String themeId = context.get("themeId");
    try {
      Map<String, Object> theme = api.getJson("/api/themes/" + themeId);
      List<Map<String, Object>> channels =
          api.getJsonList("/api/themes/" + themeId + "/hot-news/channels");

      Map<String, Object> outputs = new LinkedHashMap<>();
      outputs.put("themeId", themeId);
      outputs.put("searchSuggest", slimSuggest(context.get("searchSuggest")));
      outputs.put("githubSuggest", slimSuggest(context.get("githubSuggest")));
      outputs.put("rubricSuggest", slimRubricSuggest(context.get("rubricSuggest")));
      outputs.put("commit", slimCommit(context.get("commit")));
      outputs.put("generatedChannels", slimGenerated(context.get("generatedChannels")));
      outputs.put(
          "theme",
          Map.of(
              "code", theme.get("code"),
              "name", theme.get("name"),
              "status", theme.get("status"),
              "modeId", theme.get("modeId")));
      outputs.put("channels", HotNewsGoldenSupport.slimChannels(channels));
      return api.packArtifacts("", outputs);
    } catch (Exception exception) {
      throw new IllegalStateException("G08 采集失败", exception);
    }
  }

  private static Map<String, Object> slimSuggest(Object raw) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (!(raw instanceof Map<?, ?> map)) {
      return out;
    }
    out.put("fromAi", map.get("fromAi"));
    if (map.containsKey("queries")) {
      out.put("queries", map.get("queries"));
    }
    if (map.containsKey("query")) {
      out.put("query", map.get("query"));
    }
    return out;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> slimRubricSuggest(Object raw) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (!(raw instanceof Map<?, ?> map)) {
      return out;
    }
    out.put("fromAi", map.get("fromAi"));
    Object rubric = map.get("rubric");
    if (rubric instanceof Map<?, ?> rubricMap) {
      Map<String, Object> slim = new LinkedHashMap<>((Map<String, Object>) rubricMap);
      slim.remove("generatedAt");
      out.put("rubric", slim);
    }
    return out;
  }

  private static Map<String, Object> slimCommit(Object raw) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (!(raw instanceof Map<?, ?> map)) {
      return out;
    }
    out.put("status", map.get("status"));
    out.put("channelCount", map.get("channelCount"));
    out.put("seededChannelTypes", map.get("seededChannelTypes"));
    return out;
  }

  private static Map<String, Object> slimGenerated(Object raw) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (!(raw instanceof Map<?, ?> map)) {
      return out;
    }
    out.put("fromAi", map.get("fromAi"));
    out.put("created", map.get("created"));
    out.put("skipped", map.get("skipped"));
    return out;
  }

  @Override
  public String toString() {
    return id();
  }
}
