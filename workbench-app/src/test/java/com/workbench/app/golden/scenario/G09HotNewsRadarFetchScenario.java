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
 * G09：热点雷达采集（全图）。
 */
public final class G09HotNewsRadarFetchScenario implements GoldenScenario {
  /** 场景 ID。 */
  public static final String ID = "G09";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public void prepare(ScenarioDriver driver, GoldenScenarioContext context) {
    if (!(driver instanceof ApiV1ScenarioDriver api)) {
      throw new IllegalStateException("G09 需要 ApiV1ScenarioDriver");
    }
    HotNewsGoldenSupport.prepareHotNewsPipeline(api, context);
  }

  @Override
  public void execute(ScenarioDriver driver, GoldenScenarioContext context) {
    ApiV1ScenarioDriver api = (ApiV1ScenarioDriver) driver;
    try {
      String themeId =
          HotNewsGoldenSupport.createCommittedTheme(api, "g09_golden_theme", "黄金睡眠热点");
      context.put("themeId", themeId);
      HotNewsGoldenSupport.pointChannelsAtExternalStubs(api, themeId);

      Map<String, Object> started =
          api.postJson(
              "/api/themes/" + themeId + "/hot-news/fetch-runs",
              Map.of("hours", 72));
      String jobId = ApiV1ScenarioDriver.extractJobId(started);
      context.put("jobId", jobId);
      Map<String, Object> terminal = api.awaitTerminal(jobId, 300);
      if (!"succeeded".equals(String.valueOf(terminal.get("status")))) {
        throw new IllegalStateException("雷达采集失败: " + terminal);
      }
      context.put("job", terminal);
    } catch (RuntimeException runtime) {
      throw runtime;
    } catch (Exception exception) {
      throw new IllegalStateException("G09 执行失败", exception);
    }
  }

  @Override
  public Map<String, String> collect(ScenarioDriver driver, GoldenScenarioContext context) {
    ApiV1ScenarioDriver api = (ApiV1ScenarioDriver) driver;
    String themeId = context.get("themeId");
    String jobId = context.get("jobId");
    try {
      List<Map<String, Object>> fetchRuns =
          api.getJsonList("/api/themes/" + themeId + "/hot-news/fetch-runs");
      List<Map<String, Object>> items =
          api.getJsonList("/api/hot-news/items?themeId=" + themeId + "&limit=50");
      List<Map<String, Object>> topics =
          api.getJsonList("/api/themes/" + themeId + "/hot-news/topics");
      List<Map<String, Object>> nodeRuns = api.getJsonList("/api/jobs/" + jobId + "/nodes");
      Map<String, Object> job = context.get("job");

      Map<String, Object> outputs = new LinkedHashMap<>();
      outputs.put("themeId", themeId);
      outputs.put("jobId", jobId);
      outputs.put("job", slimJob(job));
      outputs.put("fetchRuns", slimFetchRuns(fetchRuns));
      outputs.put("items", slimItems(items));
      outputs.put("topics", slimTopics(topics));
      outputs.put("nodeRuns", CourseGoldenSupport.slimNodeRuns(nodeRuns));
      return api.packArtifacts(api.exportJobEventsJsonl(jobId), outputs);
    } catch (Exception exception) {
      throw new IllegalStateException("G09 采集失败", exception);
    }
  }

  private static Map<String, Object> slimJob(Map<String, Object> job) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (job == null) {
      return out;
    }
    out.put("status", job.get("status"));
    out.put("progress", job.get("progress"));
    out.put("type", job.get("type"));
    Object output = job.get("output");
    if (output instanceof Map<?, ?> map) {
      Map<String, Object> slim = new LinkedHashMap<>();
      slim.put("newItems", map.get("newItems"));
      slim.put("topicCount", map.get("topicCount"));
      slim.put("totalItems", map.get("totalItems"));
      slim.put("top10Count", map.get("top10Count"));
      out.put("output", slim);
    }
    return out;
  }

  private static List<Map<String, Object>> slimFetchRuns(List<Map<String, Object>> runs) {
    List<Map<String, Object>> out = new ArrayList<>();
    if (runs == null) {
      return out;
    }
    for (Map<String, Object> run : runs) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("status", run.get("status"));
      row.put("totalItems", run.get("totalItems"));
      row.put("newItems", run.get("newItems"));
      row.put("dedupedItems", run.get("dedupedItems"));
      row.put("errorSummary", run.get("errorSummary"));
      out.add(row);
    }
    return out;
  }

  private static List<Map<String, Object>> slimItems(List<Map<String, Object>> items) {
    List<Map<String, Object>> out = new ArrayList<>();
    if (items == null) {
      return out;
    }
    for (Map<String, Object> item : items) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("title", item.get("title"));
      row.put("url", item.get("url"));
      row.put("channelType", item.get("channelType"));
      row.put("originKind", item.get("originKind"));
      out.add(row);
    }
    return out;
  }

  private static List<Map<String, Object>> slimTopics(List<Map<String, Object>> topics) {
    List<Map<String, Object>> out = new ArrayList<>();
    if (topics == null) {
      return out;
    }
    for (Map<String, Object> topic : topics) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("title", topic.get("title"));
      row.put("status", topic.get("status"));
      row.put("writingScore", topic.get("writingScore"));
      row.put("heatScore", topic.get("heatScore"));
      row.put("radarRank", topic.get("radarRank"));
      row.put("originKind", topic.get("originKind"));
      row.put("mediaTypes", topic.get("mediaTypes"));
      out.add(row);
    }
    return out;
  }

  @Override
  public String toString() {
    return id();
  }
}
