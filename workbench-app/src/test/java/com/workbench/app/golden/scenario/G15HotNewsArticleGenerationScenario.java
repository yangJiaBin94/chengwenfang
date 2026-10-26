package com.workbench.app.golden.scenario;

import com.workbench.app.golden.GoldenScenario;
import com.workbench.app.golden.GoldenScenarioContext;
import com.workbench.app.golden.driver.ApiV1ScenarioDriver;
import com.workbench.app.golden.driver.ScenarioDriver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * G15：热点成文（简报确认后跑内置 12 节点工作流）。
 */
public final class G15HotNewsArticleGenerationScenario implements GoldenScenario {
  /** 场景 ID。 */
  public static final String ID = "G15";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public void prepare(ScenarioDriver driver, GoldenScenarioContext context) {
    if (!(driver instanceof ApiV1ScenarioDriver api)) {
      throw new IllegalStateException("G15 需要 ApiV1ScenarioDriver");
    }
    HotNewsGoldenSupport.prepareHotNewsPipeline(api, context);
  }

  @Override
  public void execute(ScenarioDriver driver, GoldenScenarioContext context) {
    ApiV1ScenarioDriver api = (ApiV1ScenarioDriver) driver;
    try {
      String themeId =
          HotNewsGoldenSupport.prepareThemeWithRadarAndEditorial(
              api, "g15_golden_theme", "黄金睡眠热点");
      context.put("themeId", themeId);
      String topicId = HotNewsGoldenSupport.firstRadarTopicId(api, themeId);
      context.put("topicId", topicId);

      api.postJson("/api/hot-news/topics/" + topicId + "/article-skeleton", null);
      Map<String, Object> brief =
          api.postJson("/api/hot-news/topics/" + topicId + "/briefs", null);
      String briefId = String.valueOf(brief.get("id"));
      context.put("briefId", briefId);
      api.postJson(
          "/api/hot-news/briefs/" + briefId + "/confirm",
          Map.of("note", "黄金基线确认简报后成文"));

      Map<String, Object> started =
          api.postJson("/api/hot-news/briefs/" + briefId + "/generate-article", null);
      String jobId = ApiV1ScenarioDriver.extractJobId(started);
      context.put("jobId", jobId);
      String articleId = String.valueOf(started.get("entityId"));
      context.put("articleId", articleId);

      Map<String, Object> terminal = api.awaitTerminal(jobId, 300);
      if (!"succeeded".equals(String.valueOf(terminal.get("status")))) {
        throw new IllegalStateException("热点成文失败: " + terminal);
      }
      context.put("job", terminal);
    } catch (RuntimeException runtime) {
      throw runtime;
    } catch (Exception exception) {
      throw new IllegalStateException("G15 执行失败", exception);
    }
  }

  @Override
  public Map<String, String> collect(ScenarioDriver driver, GoldenScenarioContext context) {
    ApiV1ScenarioDriver api = (ApiV1ScenarioDriver) driver;
    String articleId = context.get("articleId");
    String jobId = context.get("jobId");
    try {
      Map<String, Object> article = api.getJson("/api/hot-news/articles/" + articleId);
      List<Map<String, Object>> versions =
          api.getJsonList("/api/hot-news/articles/" + articleId + "/versions");
      List<Map<String, Object>> images =
          api.getJsonList("/api/hot-news/articles/" + articleId + "/images");
      List<Map<String, Object>> nodeRuns = api.getJsonList("/api/jobs/" + jobId + "/nodes");

      Map<String, Object> outputs = new LinkedHashMap<>();
      outputs.put("themeId", context.get("themeId"));
      outputs.put("topicId", context.get("topicId"));
      outputs.put("briefId", context.get("briefId"));
      outputs.put("articleId", articleId);
      outputs.put("jobId", jobId);
      outputs.put("article", slimArticle(article));
      outputs.put("versions", slimVersions(versions));
      outputs.put("images", CourseGoldenSupport.slimImages(images));
      outputs.put("nodeRuns", CourseGoldenSupport.slimNodeRuns(nodeRuns));
      return api.packArtifacts(api.exportJobEventsJsonl(jobId), outputs);
    } catch (Exception exception) {
      throw new IllegalStateException("G15 采集失败", exception);
    }
  }

  private static Map<String, Object> slimArticle(Map<String, Object> article) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (article == null) {
      return out;
    }
    out.put("title", article.get("title"));
    out.put("status", article.get("status"));
    out.put("currentVersionNo", article.get("currentVersionNo"));
    out.put("draftWordCount", article.get("draftWordCount"));
    out.put("wordCount", article.get("wordCount"));
    out.put("draftContent", article.get("draftContent"));
    out.put("content", article.get("content"));
    out.put("generationSettings", article.get("generationSettings"));
    out.put("sourceSnapshot", slimSourceSnapshot(article.get("sourceSnapshot")));
    return out;
  }

  private static List<Map<String, Object>> slimVersions(List<Map<String, Object>> versions) {
    List<Map<String, Object>> out = new ArrayList<>();
    if (versions == null) {
      return out;
    }
    for (Map<String, Object> version : versions) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("versionNo", version.get("versionNo"));
      row.put("source", version.get("source"));
      row.put("reason", version.get("reason"));
      out.add(row);
    }
    return out;
  }

  private static Object slimSourceSnapshot(Object snapshot) {
    if (!(snapshot instanceof List<?> list)) {
      return snapshot;
    }
    List<Map<String, Object>> out = new ArrayList<>();
    for (Object element : list) {
      if (!(element instanceof Map<?, ?> map)) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("title", map.get("title"));
      row.put("url", map.get("url"));
      row.put("role", map.get("role"));
      out.add(row);
    }
    return out;
  }

  @Override
  public String toString() {
    return id();
  }
}
