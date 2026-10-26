package com.workbench.app.golden.scenario;

import com.workbench.app.golden.GoldenScenario;
import com.workbench.app.golden.GoldenScenarioContext;
import com.workbench.app.golden.driver.ApiV1ScenarioDriver;
import com.workbench.app.golden.driver.ScenarioDriver;
import com.workbench.app.golden.stub.ExternalSiteStubs;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * G13：信源视频解析（事实速览、推广判断、生命周期）。
 */
public final class G13HotNewsSourceVideoParseScenario implements GoldenScenario {
  /** 场景 ID。 */
  public static final String ID = "G13";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public void prepare(ScenarioDriver driver, GoldenScenarioContext context) {
    if (!(driver instanceof ApiV1ScenarioDriver api)) {
      throw new IllegalStateException("G13 需要 ApiV1ScenarioDriver");
    }
    HotNewsGoldenSupport.prepareHotNewsPipeline(api, context);
  }

  @Override
  public void execute(ScenarioDriver driver, GoldenScenarioContext context) {
    ApiV1ScenarioDriver api = (ApiV1ScenarioDriver) driver;
    try {
      String themeId =
          HotNewsGoldenSupport.createCommittedTheme(api, "g13_golden_theme", "黄金睡眠热点");
      context.put("themeId", themeId);
      HotNewsGoldenSupport.pointChannelsAtExternalStubs(api, themeId);

      List<Map<String, Object>> channels =
          api.getJsonList("/api/themes/" + themeId + "/hot-news/channels");
      if (channels.isEmpty()) {
        throw new IllegalStateException("G13 主题无渠道");
      }
      String channelId = String.valueOf(channels.get(0).get("id"));
      String videoUrl =
          api.externalServer().baseUrl() + ExternalSiteStubs.PATH_VIDEO + "?v=g13-source";
      api.seedPendingSourceVideo(
          themeId, channelId, videoUrl, "Sleep mechanism video: deep rest and melatonin");

      Map<String, Object> started =
          api.postJson(
              "/api/themes/" + themeId + "/hot-news/source-items/video-parse", null);
      if (started == null || started.isEmpty()) {
        throw new IllegalStateException("信源视频解析未入队（无待解析条目）");
      }
      String jobId = ApiV1ScenarioDriver.extractJobId(started);
      context.put("jobId", jobId);
      Map<String, Object> terminal = api.awaitTerminal(jobId, 300);
      if (!"succeeded".equals(String.valueOf(terminal.get("status")))) {
        throw new IllegalStateException("信源视频解析失败: " + terminal);
      }
      context.put("job", terminal);
    } catch (RuntimeException runtime) {
      throw runtime;
    } catch (Exception exception) {
      throw new IllegalStateException("G13 执行失败", exception);
    }
  }

  @Override
  public Map<String, String> collect(ScenarioDriver driver, GoldenScenarioContext context) {
    ApiV1ScenarioDriver api = (ApiV1ScenarioDriver) driver;
    String themeId = context.get("themeId");
    String jobId = context.get("jobId");
    try {
      List<Map<String, Object>> items =
          api.getJsonList("/api/hot-news/items?themeId=" + themeId + "&limit=50");
      List<Map<String, Object>> nodeRuns = api.getJsonList("/api/jobs/" + jobId + "/nodes");
      Map<String, Object> job = context.get("job");

      Map<String, Object> outputs = new LinkedHashMap<>();
      outputs.put("themeId", themeId);
      outputs.put("jobId", jobId);
      outputs.put("job", slimJob(job));
      outputs.put("items", slimItems(items));
      outputs.put("nodeRuns", CourseGoldenSupport.slimNodeRuns(nodeRuns));
      return api.packArtifacts(api.exportJobEventsJsonl(jobId), outputs);
    } catch (Exception exception) {
      throw new IllegalStateException("G13 采集失败", exception);
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
      out.put("output", slim);
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
      return leftUrl.compareTo(rightUrl);
    });
    for (Map<String, Object> item : sorted) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("title", item.get("title"));
      row.put("url", item.get("url"));
      row.put("summary", item.get("summary"));
      row.put("mediaType", item.get("mediaType"));
      row.put("lifecycleStatus", item.get("lifecycleStatus"));
      row.put("discardReason", item.get("discardReason"));
      row.put("contentIntent", item.get("contentIntent"));
      out.add(row);
    }
    return out;
  }

  @Override
  public String toString() {
    return id();
  }
}
