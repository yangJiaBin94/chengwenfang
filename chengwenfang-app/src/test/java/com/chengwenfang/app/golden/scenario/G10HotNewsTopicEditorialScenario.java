package com.chengwenfang.app.golden.scenario;

import com.chengwenfang.app.golden.GoldenScenario;
import com.chengwenfang.app.golden.GoldenScenarioContext;
import com.chengwenfang.app.golden.driver.ApiV1ScenarioDriver;
import com.chengwenfang.app.golden.driver.ScenarioDriver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * G10：选题编辑提炼（中文标题与事实速览）。
 */
public final class G10HotNewsTopicEditorialScenario implements GoldenScenario {
  /** 场景 ID。 */
  public static final String ID = "G10";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public void prepare(ScenarioDriver driver, GoldenScenarioContext context) {
    if (!(driver instanceof ApiV1ScenarioDriver api)) {
      throw new IllegalStateException("G10 需要 ApiV1ScenarioDriver");
    }
    HotNewsGoldenSupport.prepareHotNewsPipeline(api, context);
  }

  @Override
  public void execute(ScenarioDriver driver, GoldenScenarioContext context) {
    ApiV1ScenarioDriver api = (ApiV1ScenarioDriver) driver;
    try {
      String themeId =
          HotNewsGoldenSupport.createCommittedTheme(api, "g10_golden_theme", "黄金睡眠热点");
      context.put("themeId", themeId);
      HotNewsGoldenSupport.pointChannelsAtExternalStubs(api, themeId);

      Map<String, Object> started =
          api.postJson(
              "/api/themes/" + themeId + "/hot-news/fetch-runs",
              Map.of("hours", 72));
      String fetchJobId = ApiV1ScenarioDriver.extractJobId(started);
      Map<String, Object> fetchTerminal = api.awaitTerminal(fetchJobId, 300);
      if (!"succeeded".equals(String.valueOf(fetchTerminal.get("status")))) {
        throw new IllegalStateException("G10 雷达采集失败: " + fetchTerminal);
      }
      context.put("fetchJobId", fetchJobId);

      Map<String, Object> editorial =
          api.postJson(
              "/api/themes/" + themeId + "/hot-news/topics/editorialize", null);
      context.put("editorial", editorial);
    } catch (RuntimeException runtime) {
      throw runtime;
    } catch (Exception exception) {
      throw new IllegalStateException("G10 执行失败", exception);
    }
  }

  @Override
  public Map<String, String> collect(ScenarioDriver driver, GoldenScenarioContext context) {
    ApiV1ScenarioDriver api = (ApiV1ScenarioDriver) driver;
    String themeId = context.get("themeId");
    try {
      List<Map<String, Object>> topics =
          api.getJsonList("/api/themes/" + themeId + "/hot-news/topics");
      Map<String, Object> editorial = context.get("editorial");

      Map<String, Object> outputs = new LinkedHashMap<>();
      outputs.put("themeId", themeId);
      outputs.put("editorial", slimEditorial(editorial));
      outputs.put("topics", slimTopics(topics));
      return api.packArtifacts("", outputs);
    } catch (Exception exception) {
      throw new IllegalStateException("G10 采集失败", exception);
    }
  }

  private static Map<String, Object> slimEditorial(Map<String, Object> editorial) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (editorial == null) {
      return out;
    }
    out.put("translatedTopics", editorial.get("translatedTopics"));
    return out;
  }

  private static List<Map<String, Object>> slimTopics(List<Map<String, Object>> topics) {
    List<Map<String, Object>> out = new ArrayList<>();
    if (topics == null) {
      return out;
    }
    for (Map<String, Object> topic : topics) {
      Map<String, Object> row = new LinkedHashMap<>();
      // 列表视图已把 translated* 合并进 title/summary
      row.put("title", topic.get("title"));
      row.put("summary", topic.get("summary"));
      row.put("status", topic.get("status"));
      row.put("writingScore", topic.get("writingScore"));
      out.add(row);
    }
    return out;
  }

  @Override
  public String toString() {
    return id();
  }
}
