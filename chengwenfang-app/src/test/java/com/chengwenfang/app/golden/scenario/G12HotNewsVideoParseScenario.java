package com.chengwenfang.app.golden.scenario;

import com.chengwenfang.app.golden.GoldenScenario;
import com.chengwenfang.app.golden.GoldenScenarioContext;
import com.chengwenfang.app.golden.driver.ApiV1ScenarioDriver;
import com.chengwenfang.app.golden.driver.ScenarioDriver;
import com.chengwenfang.app.golden.stub.ExternalSiteStubs;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * G12：资料池视频解析（CLI 替身 + 语音/视觉摘要 + 重新评分）。
 */
public final class G12HotNewsVideoParseScenario implements GoldenScenario {
  /** 场景 ID。 */
  public static final String ID = "G12";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public void prepare(ScenarioDriver driver, GoldenScenarioContext context) {
    if (!(driver instanceof ApiV1ScenarioDriver api)) {
      throw new IllegalStateException("G12 需要 ApiV1ScenarioDriver");
    }
    HotNewsGoldenSupport.prepareHotNewsPipeline(api, context);
  }

  @Override
  public void execute(ScenarioDriver driver, GoldenScenarioContext context) {
    ApiV1ScenarioDriver api = (ApiV1ScenarioDriver) driver;
    try {
      String themeId =
          HotNewsGoldenSupport.prepareThemeWithRadarAndEditorial(
              api, "g12_golden_theme", "黄金睡眠热点");
      context.put("themeId", themeId);
      String topicId = HotNewsGoldenSupport.firstRadarTopicId(api, themeId);
      context.put("topicId", topicId);

      String videoUrl =
          api.externalServer().baseUrl() + ExternalSiteStubs.PATH_VIDEO + "?v=golden-sleep";
      Map<String, Object> added =
          api.postJson(
              "/api/hot-news/topics/" + topicId + "/research-items",
              Map.of("url", videoUrl));
      context.put("videoItemId", String.valueOf(added.get("id")));

      Map<String, Object> started =
          api.postJson(
              "/api/hot-news/topics/" + topicId + "/research-items/video-parse", null);
      String jobId = ApiV1ScenarioDriver.extractJobId(started);
      context.put("jobId", jobId);
      Map<String, Object> terminal = api.awaitTerminal(jobId, 300);
      if (!"succeeded".equals(String.valueOf(terminal.get("status")))) {
        throw new IllegalStateException("资料池视频解析失败: " + terminal);
      }
      context.put("job", terminal);
    } catch (RuntimeException runtime) {
      throw runtime;
    } catch (Exception exception) {
      throw new IllegalStateException("G12 执行失败", exception);
    }
  }

  @Override
  public Map<String, String> collect(ScenarioDriver driver, GoldenScenarioContext context) {
    ApiV1ScenarioDriver api = (ApiV1ScenarioDriver) driver;
    String topicId = context.get("topicId");
    String jobId = context.get("jobId");
    try {
      List<Map<String, Object>> items =
          api.getJsonList("/api/hot-news/topics/" + topicId + "/research-items");
      List<Map<String, Object>> nodeRuns = api.getJsonList("/api/jobs/" + jobId + "/nodes");
      Map<String, Object> job = context.get("job");

      Map<String, Object> outputs = new LinkedHashMap<>();
      outputs.put("themeId", context.get("themeId"));
      outputs.put("topicId", topicId);
      outputs.put("videoItemId", context.get("videoItemId"));
      outputs.put("jobId", jobId);
      outputs.put("job", slimJob(job));
      outputs.put("researchItems", slimItems(items));
      outputs.put("nodeRuns", CourseGoldenSupport.slimNodeRuns(nodeRuns));
      return api.packArtifacts(api.exportJobEventsJsonl(jobId), outputs);
    } catch (Exception exception) {
      throw new IllegalStateException("G12 采集失败", exception);
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
      slim.put("ok", map.get("ok"));
      slim.put("ready", map.get("ready"));
      slim.put("discarded", map.get("discarded"));
      slim.put("parsed", map.get("parsed"));
      slim.put("total", map.get("total"));
      out.put("output", slim);
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
      row.put("summary", item.get("summary"));
      row.put("url", item.get("url"));
      row.put("kind", item.get("kind"));
      row.put("mediaLabel", item.get("mediaLabel"));
      row.put("score", item.get("score"));
      row.put("videoParseStatus", item.get("videoParseStatus"));
      row.put("videoParseStep", item.get("videoParseStep"));
      row.put("videoContentKind", item.get("videoContentKind"));
      row.put("videoDurationSeconds", item.get("videoDurationSeconds"));
      out.add(row);
    }
    return out;
  }

  @Override
  public String toString() {
    return id();
  }
}
