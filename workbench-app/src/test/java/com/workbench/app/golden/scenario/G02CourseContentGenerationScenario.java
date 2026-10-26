package com.workbench.app.golden.scenario;

import com.workbench.app.golden.GoldenScenario;
import com.workbench.app.golden.GoldenScenarioContext;
import com.workbench.app.golden.driver.ApiV1ScenarioDriver;
import com.workbench.app.golden.driver.ScenarioDriver;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * G02：课程正式生成（全图）。
 */
public final class G02CourseContentGenerationScenario implements GoldenScenario {
  /** 场景 ID。 */
  public static final String ID = "G02";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public void prepare(ScenarioDriver driver, GoldenScenarioContext context) {
    if (!(driver instanceof ApiV1ScenarioDriver api)) {
      throw new IllegalStateException("G02 需要 ApiV1ScenarioDriver");
    }
    CourseGoldenSupport.prepareCoursePipeline(api, context);
  }

  @Override
  public void execute(ScenarioDriver driver, GoldenScenarioContext context) {
    ApiV1ScenarioDriver api = (ApiV1ScenarioDriver) driver;
    try {
      Map<String, String> ids = CourseGoldenSupport.createCommittedCourseShell(api);
      String courseId = ids.get("courseId");
      context.put("themeId", ids.get("themeId"));
      context.put("courseId", courseId);

      Map<String, Object> started =
          api.postJson(
              "/api/courses/" + courseId + "/generate-content",
              Map.of("wordCountOption", "2000-4000", "imageCountOption", "up-to-3"));
      String jobId = ApiV1ScenarioDriver.extractJobId(started);
      context.put("jobId", jobId);
      Map<String, Object> terminal = api.awaitTerminal(jobId, 180);
      if (!"succeeded".equals(String.valueOf(terminal.get("status")))) {
        throw new IllegalStateException("课程正式生成失败: " + terminal);
      }
      context.put("job", terminal);
    } catch (RuntimeException runtime) {
      throw runtime;
    } catch (Exception exception) {
      throw new IllegalStateException("G02 执行失败", exception);
    }
  }

  @Override
  public Map<String, String> collect(ScenarioDriver driver, GoldenScenarioContext context) {
    ApiV1ScenarioDriver api = (ApiV1ScenarioDriver) driver;
    String courseId = context.get("courseId");
    String jobId = context.get("jobId");
    try {
      Map<String, Object> content = api.getJson("/api/courses/" + courseId + "/content");
      String draftArticleId = String.valueOf(content.get("draftArticleId"));
      String articleArticleId = String.valueOf(content.get("articleArticleId"));
      List<Map<String, Object>> draftVersions =
          api.getJsonList("/api/articles/" + draftArticleId + "/versions");
      List<Map<String, Object>> wechatVersions =
          api.getJsonList("/api/articles/" + articleArticleId + "/versions");
      List<Map<String, Object>> images =
          api.getJsonList("/api/articles/" + articleArticleId + "/images");
      List<Map<String, Object>> nodeRuns = api.getJsonList("/api/jobs/" + jobId + "/nodes");

      Map<String, Object> outputs = new LinkedHashMap<>();
      outputs.put("courseId", courseId);
      outputs.put("jobId", jobId);
      outputs.put(
          "content",
          Map.of(
              "draft", content.get("draft"),
              "article", content.get("article")));
      outputs.put("draftVersions", slimVersions(draftVersions));
      outputs.put("wechatVersions", slimVersions(wechatVersions));
      outputs.put("images", CourseGoldenSupport.slimImages(images));
      outputs.put("nodeRuns", CourseGoldenSupport.slimNodeRuns(nodeRuns));
      return api.packArtifacts(api.exportJobEventsJsonl(jobId), outputs);
    } catch (Exception exception) {
      throw new IllegalStateException("G02 采集失败", exception);
    }
  }

  private static List<Map<String, Object>> slimVersions(List<Map<String, Object>> versions) {
    java.util.ArrayList<Map<String, Object>> out = new java.util.ArrayList<>();
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

  @Override
  public String toString() {
    return id();
  }
}
