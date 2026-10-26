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
 * G11：选题调研（关键词、DDG、相关性、翻译、资料评分）。
 */
public final class G11HotNewsTopicResearchScenario implements GoldenScenario {
  /** 场景 ID。 */
  public static final String ID = "G11";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public void prepare(ScenarioDriver driver, GoldenScenarioContext context) {
    if (!(driver instanceof ApiV1ScenarioDriver api)) {
      throw new IllegalStateException("G11 需要 ApiV1ScenarioDriver");
    }
    HotNewsGoldenSupport.prepareHotNewsPipeline(api, context);
  }

  @Override
  public void execute(ScenarioDriver driver, GoldenScenarioContext context) {
    ApiV1ScenarioDriver api = (ApiV1ScenarioDriver) driver;
    try {
      String themeId =
          HotNewsGoldenSupport.prepareThemeWithRadarAndEditorial(
              api, "g11_golden_theme", "黄金睡眠热点");
      context.put("themeId", themeId);
      String topicId = HotNewsGoldenSupport.firstRadarTopicId(api, themeId);
      context.put("topicId", topicId);

      Map<String, Object> started =
          api.postJson("/api/hot-news/topics/" + topicId + "/research-runs", null);
      String jobId = ApiV1ScenarioDriver.extractJobId(started);
      context.put("jobId", jobId);
      Map<String, Object> terminal = api.awaitTerminal(jobId, 300);
      if (!"succeeded".equals(String.valueOf(terminal.get("status")))) {
        throw new IllegalStateException("选题调研失败: " + terminal);
      }
      context.put("job", terminal);
    } catch (RuntimeException runtime) {
      throw runtime;
    } catch (Exception exception) {
      throw new IllegalStateException("G11 执行失败", exception);
    }
  }

  @Override
  public Map<String, String> collect(ScenarioDriver driver, GoldenScenarioContext context) {
    ApiV1ScenarioDriver api = (ApiV1ScenarioDriver) driver;
    String topicId = context.get("topicId");
    String jobId = context.get("jobId");
    try {
      List<Map<String, Object>> runs =
          api.getJsonList("/api/hot-news/topics/" + topicId + "/research-runs");
      List<Map<String, Object>> items =
          api.getJsonList("/api/hot-news/topics/" + topicId + "/research-items");
      List<Map<String, Object>> conflicts =
          api.getJsonList("/api/hot-news/topics/" + topicId + "/research-conflicts");
      List<Map<String, Object>> nodeRuns = api.getJsonList("/api/jobs/" + jobId + "/nodes");
      Map<String, Object> job = context.get("job");

      Map<String, Object> outputs = new LinkedHashMap<>();
      outputs.put("themeId", context.get("themeId"));
      outputs.put("topicId", topicId);
      outputs.put("jobId", jobId);
      outputs.put("job", slimJob(job));
      outputs.put("researchRuns", slimRuns(runs));
      outputs.put("researchItems", slimItems(items));
      outputs.put("conflicts", slimConflicts(conflicts));
      outputs.put("nodeRuns", CourseGoldenSupport.slimNodeRuns(nodeRuns));
      return api.packArtifacts(api.exportJobEventsJsonl(jobId), outputs);
    } catch (Exception exception) {
      throw new IllegalStateException("G11 采集失败", exception);
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
    return out;
  }

  private static List<Map<String, Object>> slimRuns(List<Map<String, Object>> runs) {
    List<Map<String, Object>> out = new ArrayList<>();
    if (runs == null) {
      return out;
    }
    for (Map<String, Object> run : runs) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("status", run.get("status"));
      row.put("errorMessage", run.get("errorMessage"));
      out.add(row);
    }
    return out;
  }

  private static List<Map<String, Object>> slimItems(List<Map<String, Object>> items) {
    List<Map<String, Object>> out = new ArrayList<>();
    if (items == null) {
      return out;
    }
    List<Map<String, Object>> sorted = new ArrayList<>(items);
    sorted.sort((left, right) -> {
      String leftUrl = String.valueOf(left == null ? "" : left.get("url"));
      String rightUrl = String.valueOf(right == null ? "" : right.get("url"));
      int byUrl = leftUrl.compareTo(rightUrl);
      if (byUrl != 0) {
        return byUrl;
      }
      String leftTitle = String.valueOf(left == null ? "" : left.get("title"));
      String rightTitle = String.valueOf(right == null ? "" : right.get("title"));
      return leftTitle.compareTo(rightTitle);
    });
    for (Map<String, Object> item : sorted) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("title", item.get("title"));
      row.put("summary", item.get("summary"));
      row.put("url", item.get("url"));
      row.put("kind", item.get("kind"));
      row.put("score", item.get("score"));
      row.put("scoreDetails", slimScoreDetails(item.get("scoreDetails")));
      row.put("sourceLabel", item.get("sourceLabel"));
      row.put("mediaLabel", item.get("mediaLabel"));
      out.add(row);
    }
    return out;
  }

  private static Object slimScoreDetails(Object scoreDetails) {
    if (!(scoreDetails instanceof Map<?, ?> map)) {
      return scoreDetails;
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("rubricVersion", map.get("rubricVersion"));
    out.put("recommendUse", map.get("recommendUse"));
    out.put("riskNote", map.get("riskNote"));
    out.put("dimensions", map.get("dimensions"));
    return out;
  }

  private static List<Map<String, Object>> slimConflicts(List<Map<String, Object>> conflicts) {
    List<Map<String, Object>> out = new ArrayList<>();
    if (conflicts == null) {
      return out;
    }
    for (Map<String, Object> conflict : conflicts) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("claim", conflict.get("claim"));
      row.put("detail", conflict.get("detail"));
      out.add(row);
    }
    return out;
  }

  @Override
  public String toString() {
    return id();
  }
}
