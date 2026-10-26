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
 * G14：文章骨架与简报（创建、确认，含来源快照）。
 */
public final class G14HotNewsSkeletonBriefScenario implements GoldenScenario {
  /** 场景 ID。 */
  public static final String ID = "G14";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public void prepare(ScenarioDriver driver, GoldenScenarioContext context) {
    if (!(driver instanceof ApiV1ScenarioDriver api)) {
      throw new IllegalStateException("G14 需要 ApiV1ScenarioDriver");
    }
    HotNewsGoldenSupport.prepareHotNewsPipeline(api, context);
  }

  @Override
  public void execute(ScenarioDriver driver, GoldenScenarioContext context) {
    ApiV1ScenarioDriver api = (ApiV1ScenarioDriver) driver;
    try {
      String themeId =
          HotNewsGoldenSupport.prepareThemeWithRadarAndEditorial(
              api, "g14_golden_theme", "黄金睡眠热点");
      context.put("themeId", themeId);
      String topicId = HotNewsGoldenSupport.firstRadarTopicId(api, themeId);
      context.put("topicId", topicId);

      Map<String, Object> skeleton =
          api.postJson("/api/hot-news/topics/" + topicId + "/article-skeleton", null);
      context.put("skeleton", skeleton);

      Map<String, Object> brief =
          api.postJson("/api/hot-news/topics/" + topicId + "/briefs", null);
      String briefId = String.valueOf(brief.get("id"));
      context.put("briefId", briefId);

      Map<String, Object> confirmed =
          api.postJson(
              "/api/hot-news/briefs/" + briefId + "/confirm",
              Map.of("note", "黄金基线确认简报"));
      context.put("confirmedBrief", confirmed);
    } catch (RuntimeException runtime) {
      throw runtime;
    } catch (Exception exception) {
      throw new IllegalStateException("G14 执行失败", exception);
    }
  }

  @Override
  public Map<String, String> collect(ScenarioDriver driver, GoldenScenarioContext context) {
    ApiV1ScenarioDriver api = (ApiV1ScenarioDriver) driver;
    String topicId = context.get("topicId");
    try {
      Map<String, Object> skeleton =
          api.getJson("/api/hot-news/topics/" + topicId + "/article-skeleton");
      List<Map<String, Object>> briefs =
          api.getJsonList("/api/hot-news/topics/" + topicId + "/briefs");

      Map<String, Object> outputs = new LinkedHashMap<>();
      outputs.put("themeId", context.get("themeId"));
      outputs.put("topicId", topicId);
      outputs.put("skeleton", slimSkeleton(skeleton));
      outputs.put("briefs", slimBriefs(briefs));
      return api.packArtifacts("", outputs);
    } catch (Exception exception) {
      throw new IllegalStateException("G14 采集失败", exception);
    }
  }

  private static Map<String, Object> slimSkeleton(Map<String, Object> skeleton) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (skeleton == null) {
      return out;
    }
    out.put("workingTitle", skeleton.get("workingTitle"));
    out.put("scope", skeleton.get("scope"));
    out.put("coreThesis", skeleton.get("coreThesis"));
    out.put("coreEvent", skeleton.get("coreEvent"));
    out.put("keyInsights", skeleton.get("keyInsights"));
    out.put("impacts", skeleton.get("impacts"));
    out.put("readerSignals", skeleton.get("readerSignals"));
    out.put("narrativeShape", skeleton.get("narrativeShape"));
    out.put("sectionPlan", skeleton.get("sectionPlan"));
    out.put("illustrationStyle", skeleton.get("illustrationStyle"));
    out.put("coverBrief", skeleton.get("coverBrief"));
    out.put("recommendedWordCountOption", skeleton.get("recommendedWordCountOption"));
    out.put("recommendedImageCountOption", skeleton.get("recommendedImageCountOption"));
    out.put("architecture", skeleton.get("architecture"));
    out.put("flow", skeleton.get("flow"));
    return out;
  }

  private static List<Map<String, Object>> slimBriefs(List<Map<String, Object>> briefs) {
    List<Map<String, Object>> out = new ArrayList<>();
    if (briefs == null) {
      return out;
    }
    for (Map<String, Object> brief : briefs) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("status", brief.get("status"));
      row.put("title", brief.get("title"));
      row.put("articleType", brief.get("articleType"));
      row.put("confirmationNote", brief.get("confirmationNote"));
      row.put("sourceSnapshot", slimSourceSnapshot(brief.get("sourceSnapshot")));
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
