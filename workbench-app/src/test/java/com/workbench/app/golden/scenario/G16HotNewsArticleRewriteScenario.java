package com.workbench.app.golden.scenario;

import com.workbench.app.golden.GoldenScenario;
import com.workbench.app.golden.GoldenScenarioContext;
import com.workbench.app.golden.driver.ApiV1ScenarioDriver;
import com.workbench.app.golden.driver.ScenarioDriver;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * G16：热点文章 AI 改写（成文成功后入队改写）。
 */
public final class G16HotNewsArticleRewriteScenario implements GoldenScenario {
  /** 场景 ID。 */
  public static final String ID = "G16";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public void prepare(ScenarioDriver driver, GoldenScenarioContext context) {
    if (!(driver instanceof ApiV1ScenarioDriver api)) {
      throw new IllegalStateException("G16 需要 ApiV1ScenarioDriver");
    }
    HotNewsGoldenSupport.prepareHotNewsPipeline(api, context);
  }

  @Override
  public void execute(ScenarioDriver driver, GoldenScenarioContext context) {
    ApiV1ScenarioDriver api = (ApiV1ScenarioDriver) driver;
    try {
      String themeId =
          HotNewsGoldenSupport.prepareThemeWithRadarAndEditorial(
              api, "g16_golden_theme", "黄金睡眠热点");
      context.put("themeId", themeId);
      String topicId = HotNewsGoldenSupport.firstRadarTopicId(api, themeId);
      context.put("topicId", topicId);

      api.postJson("/api/hot-news/topics/" + topicId + "/article-skeleton", null);
      Map<String, Object> brief =
          api.postJson("/api/hot-news/topics/" + topicId + "/briefs", null);
      String briefId = String.valueOf(brief.get("id"));
      api.postJson(
          "/api/hot-news/briefs/" + briefId + "/confirm",
          Map.of("note", "黄金基线确认简报后改写"));

      Map<String, Object> generated =
          api.postJson("/api/hot-news/briefs/" + briefId + "/generate-article", null);
      String generateJobId = ApiV1ScenarioDriver.extractJobId(generated);
      String articleId = String.valueOf(generated.get("entityId"));
      context.put("articleId", articleId);
      Map<String, Object> generateTerminal = api.awaitTerminal(generateJobId, 300);
      if (!"succeeded".equals(String.valueOf(generateTerminal.get("status")))) {
        throw new IllegalStateException("热点成文失败: " + generateTerminal);
      }

      Map<String, Object> started =
          api.postJson(
              "/api/hot-news/articles/" + articleId + "/rewrite",
              Map.of("instruction", "缩短开场，保留事实与配图占位"));
      String jobId = ApiV1ScenarioDriver.extractJobId(started);
      context.put("jobId", jobId);
      Map<String, Object> terminal = api.awaitTerminal(jobId, 120);
      if (!"succeeded".equals(String.valueOf(terminal.get("status")))) {
        throw new IllegalStateException("热点改写失败: " + terminal);
      }
      context.put("job", terminal);
    } catch (RuntimeException runtime) {
      throw runtime;
    } catch (Exception exception) {
      throw new IllegalStateException("G16 执行失败", exception);
    }
  }

  @Override
  public Map<String, String> collect(ScenarioDriver driver, GoldenScenarioContext context) {
    ApiV1ScenarioDriver api = (ApiV1ScenarioDriver) driver;
    String articleId = context.get("articleId");
    String jobId = context.get("jobId");
    try {
      Map<String, Object> article = api.getJson("/api/hot-news/articles/" + articleId);
      Map<String, Object> settings = asMap(article.get("generationSettings"));

      Map<String, Object> outputs = new LinkedHashMap<>();
      outputs.put("themeId", context.get("themeId"));
      outputs.put("topicId", context.get("topicId"));
      outputs.put("articleId", articleId);
      outputs.put("jobId", jobId);
      outputs.put(
          "article",
          Map.of(
              "status", article.get("status"),
              "draftContent", article.get("draftContent"),
              "content", article.get("content"),
              "currentVersionNo", article.get("currentVersionNo")));
      Map<String, Object> rewriteSettings = new LinkedHashMap<>();
      rewriteSettings.put("lastRewriteInstruction", settings.get("lastRewriteInstruction"));
      rewriteSettings.put("rewrittenAt", settings.get("rewrittenAt"));
      outputs.put("rewriteSettings", rewriteSettings);
      return api.packArtifacts(api.exportJobEventsJsonl(jobId), outputs);
    } catch (Exception exception) {
      throw new IllegalStateException("G16 采集失败", exception);
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value) {
    if (value instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    return Map.of();
  }

  @Override
  public String toString() {
    return id();
  }
}
