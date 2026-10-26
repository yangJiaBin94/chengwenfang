package com.workbench.app.golden.scenario;

import com.workbench.app.golden.GoldenScenario;
import com.workbench.app.golden.GoldenScenarioContext;
import com.workbench.app.golden.driver.ApiV1ScenarioDriver;
import com.workbench.app.golden.driver.ScenarioDriver;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * G19：终态通知（成功雷达采集 + 失败热点改写各一次）。
 */
public final class G19TerminalNotificationScenario implements GoldenScenario {
  /** 场景 ID。 */
  public static final String ID = "G19";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public void prepare(ScenarioDriver driver, GoldenScenarioContext context) {
    if (!(driver instanceof ApiV1ScenarioDriver api)) {
      throw new IllegalStateException("G19 需要 ApiV1ScenarioDriver");
    }
    HotNewsGoldenSupport.prepareHotNewsPipeline(api, context);
  }

  @Override
  public void execute(ScenarioDriver driver, GoldenScenarioContext context) {
    ApiV1ScenarioDriver api = (ApiV1ScenarioDriver) driver;
    try {
      // 成功：雷达采集（prepare 内已 await 成功）
      String themeId =
          HotNewsGoldenSupport.prepareThemeWithRadarAndEditorial(
              api, "g19_golden_theme", "黄金睡眠热点");
      context.put("themeId", themeId);
      String topicId = HotNewsGoldenSupport.firstRadarTopicId(api, themeId);
      context.put("topicId", topicId);

      api.postJson("/api/hot-news/topics/" + topicId + "/article-skeleton", null);
      Map<String, Object> brief =
          api.postJson("/api/hot-news/topics/" + topicId + "/briefs", null);
      String briefId = String.valueOf(brief.get("id"));
      api.postJson(
          "/api/hot-news/briefs/" + briefId + "/confirm",
          Map.of("note", "黄金基线确认简报后测通知"));

      Map<String, Object> generated =
          api.postJson("/api/hot-news/briefs/" + briefId + "/generate-article", null);
      String generateJobId = ApiV1ScenarioDriver.extractJobId(generated);
      String articleId = String.valueOf(generated.get("entityId"));
      context.put("articleId", articleId);
      Map<String, Object> generateTerminal = api.awaitTerminal(generateJobId, 300);
      if (!"succeeded".equals(String.valueOf(generateTerminal.get("status")))) {
        throw new IllegalStateException("热点成文失败: " + generateTerminal);
      }

      // 失败：改写命中 forceEmpty fixture → 任务失败并产生失败通知
      Map<String, Object> rewriteStarted =
          api.postJson(
              "/api/hot-news/articles/" + articleId + "/rewrite",
              Map.of("instruction", "GOLDEN_FORCE_EMPTY_AI 触发空响应"));
      String rewriteJobId = ApiV1ScenarioDriver.extractJobId(rewriteStarted);
      context.put("failedJobId", rewriteJobId);
      Map<String, Object> rewriteTerminal = api.awaitTerminal(rewriteJobId, 120);
      if (!"failed".equals(String.valueOf(rewriteTerminal.get("status")))) {
        throw new IllegalStateException("期望改写失败，实际: " + rewriteTerminal);
      }
    } catch (RuntimeException runtime) {
      throw runtime;
    } catch (Exception exception) {
      throw new IllegalStateException("G19 执行失败", exception);
    }
  }

  @Override
  public Map<String, String> collect(ScenarioDriver driver, GoldenScenarioContext context) {
    ApiV1ScenarioDriver api = (ApiV1ScenarioDriver) driver;
    try {
      // 终态通知经 AFTER_COMMIT 异步落库，短轮询避免采集早于失败通知
      Object items = null;
      for (int attempt = 0; attempt < 40; attempt++) {
        Map<String, Object> list = api.getJson("/api/notifications?limit=100");
        items = list.get("items");
        if (hasFailedRewriteNotification(items)) {
          break;
        }
        Thread.sleep(50L);
      }
      if (!hasFailedRewriteNotification(items)) {
        throw new IllegalStateException("G19 未等到改写失败通知");
      }
      Map<String, Object> outputs = new LinkedHashMap<>();
      outputs.put("themeId", context.get("themeId"));
      outputs.put("articleId", context.get("articleId"));
      outputs.put("failedJobId", context.get("failedJobId"));
      outputs.put("notifications", slimNotifications(items));
      return api.packArtifacts("", outputs);
    } catch (RuntimeException runtime) {
      throw runtime;
    } catch (Exception exception) {
      throw new IllegalStateException("G19 采集失败", exception);
    }
  }

  /**
   * 是否已出现改写失败终态通知。
   *
   * @param items 通知列表
   * @return 存在 job_terminal_failed 且 metadata.jobType=hot_news_article_rewrite 时为 true
   */
  private static boolean hasFailedRewriteNotification(Object items) {
    if (!(items instanceof List<?> list)) {
      return false;
    }
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> map)) {
        continue;
      }
      if (!"job_terminal_failed".equals(String.valueOf(map.get("type")))) {
        continue;
      }
      Object metadata = map.get("metadata");
      if (metadata instanceof Map<?, ?> meta
          && "hot_news_article_rewrite".equals(String.valueOf(meta.get("jobType")))) {
        return true;
      }
    }
    return false;
  }

  private static List<Map<String, Object>> slimNotifications(Object items) {
    List<Map<String, Object>> out = new ArrayList<>();
    if (!(items instanceof List<?> list)) {
      return out;
    }
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> map)) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("type", map.get("type"));
      row.put("level", map.get("level"));
      row.put("title", map.get("title"));
      row.put("message", map.get("message"));
      row.put("metadata", slimMetadata(map.get("metadata")));
      out.add(row);
    }
    out.sort(
        Comparator.comparing((Map<String, Object> row) -> String.valueOf(row.get("type")))
            .thenComparing(row -> String.valueOf(row.get("level")))
            .thenComparing(row -> String.valueOf(row.get("title"))));
    return out;
  }

  private static Map<String, Object> slimMetadata(Object metadata) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (!(metadata instanceof Map<?, ?> map)) {
      return out;
    }
    // 跳转所需稳定字段；去掉易变 ID/时间
    for (String key :
        List.of(
            "jobType",
            "themeId",
            "topicId",
            "articleId",
            "documentId",
            "modeId",
            "entityType",
            "targetKind")) {
      if (map.containsKey(key)) {
        out.put(key, map.get(key));
      }
    }
    return out;
  }

  @Override
  public String toString() {
    return id();
  }
}
