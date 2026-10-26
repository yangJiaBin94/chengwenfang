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
 * G20：自定义 AI 节点工作流（创建 → 保存草稿 → 发布 → 按自定义节点执行）。
 */
public final class G20CustomAiWorkflowScenario implements GoldenScenario {
  /** 场景 ID。 */
  public static final String ID = "G20";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public void prepare(ScenarioDriver driver, GoldenScenarioContext context) {
    if (!(driver instanceof ApiV1ScenarioDriver api)) {
      throw new IllegalStateException("G20 需要 ApiV1ScenarioDriver");
    }
    CourseGoldenSupport.prepareCoursePipeline(api, context);
  }

  @Override
  public void execute(ScenarioDriver driver, GoldenScenarioContext context) {
    ApiV1ScenarioDriver api = (ApiV1ScenarioDriver) driver;
    try {
      Map<String, Object> created =
          api.postJson(
              "/api/workflows",
              Map.of(
                  "name", "黄金自定义 AI 工作流",
                  "scene", "course_content_generation",
                  "modeId", "course-system"));
      String workflowId = String.valueOf(created.get("id"));
      context.put("workflowId", workflowId);

      Map<String, Object> draft = api.getJson("/api/workflows/" + workflowId + "/draft");
      Map<String, Object> definition = customAiDefinition();
      api.putJson(
          "/api/workflows/" + workflowId + "/draft",
          Map.of("revision", draft.get("revision"), "definition", definition));
      api.postJson(
          "/api/workflows/" + workflowId + "/publish",
          Map.of("note", "黄金基线发布自定义 AI 图"));
      // 发布后启用，否则 executeGraphForJob 拒绝停用定义
      api.patchJson(
          "/api/workflows/" + workflowId,
          Map.of("status", "active"));

      Map<String, String> ids = CourseGoldenSupport.createCommittedCourseShell(api);
      context.put("themeId", ids.get("themeId"));
      String courseId = ids.get("courseId");
      context.put("courseId", courseId);

      Map<String, Object> started =
          api.postJson(
              "/api/courses/" + courseId + "/generate-content",
              Map.of(
                  "workflowId", workflowId,
                  "wordCountOption", "2000-4000",
                  "imageCountOption", "up-to-3"));
      String jobId = ApiV1ScenarioDriver.extractJobId(started);
      context.put("jobId", jobId);
      Map<String, Object> terminal = api.awaitTerminal(jobId, 180);
      if (!"succeeded".equals(String.valueOf(terminal.get("status")))) {
        throw new IllegalStateException("自定义工作流执行失败: " + terminal);
      }
      context.put("job", terminal);
    } catch (RuntimeException runtime) {
      throw runtime;
    } catch (Exception exception) {
      throw new IllegalStateException("G20 执行失败", exception);
    }
  }

  @Override
  public Map<String, String> collect(ScenarioDriver driver, GoldenScenarioContext context) {
    ApiV1ScenarioDriver api = (ApiV1ScenarioDriver) driver;
    String courseId = context.get("courseId");
    String jobId = context.get("jobId");
    String workflowId = context.get("workflowId");
    try {
      Map<String, Object> job = api.getJson("/api/jobs/" + jobId);
      List<Map<String, Object>> nodeRuns = api.getJsonList("/api/jobs/" + jobId + "/nodes");
      List<Map<String, Object>> runs = api.getJsonList("/api/workflows/" + workflowId + "/runs");
      Object jobOutput = job.get("output");
      Object draft = null;
      if (jobOutput instanceof Map<?, ?> outputMap) {
        draft = outputMap.get("draft");
      }

      Map<String, Object> outputs = new LinkedHashMap<>();
      outputs.put("workflowId", workflowId);
      outputs.put("courseId", courseId);
      outputs.put("jobId", jobId);
      outputs.put("jobStatus", job.get("status"));
      outputs.put("draft", draft);
      outputs.put("nodeRuns", CourseGoldenSupport.slimNodeRuns(nodeRuns));
      outputs.put("workflowRuns", slimRuns(runs));
      return api.packArtifacts(api.exportJobEventsJsonl(jobId), outputs);
    } catch (Exception exception) {
      throw new IllegalStateException("G20 采集失败", exception);
    }
  }

  private static Map<String, Object> customAiDefinition() {
    Map<String, Object> customConfig = new LinkedHashMap<>();
    customConfig.put("timeoutSeconds", 60);
    customConfig.put("maxAttempts", 1);
    customConfig.put("failurePolicy", "stop");
    // custom_ai 的 write_draft 要求状态里已有字符串 draft；用 llm_text 先写出初稿
    customConfig.put(
        "prompt", "黄金自定义节点：请把当前原稿改写成更短的自定义草稿，并输出 actions JSON。");

    List<Map<String, Object>> nodes = new ArrayList<>();
    nodes.add(node("n_start", "start", "Start", 0, Map.of()));
    nodes.add(node("n_seed", "llm_text", "Seed Draft", 1, Map.of(
        "timeoutSeconds", 60,
        "maxAttempts", 1,
        "failurePolicy", "stop")));
    nodes.add(node("n_custom", "custom_ai", "Custom AI", 2, customConfig));
    nodes.add(node("n_end", "end", "End", 3, Map.of()));

    List<Map<String, Object>> edges = new ArrayList<>();
    edges.add(edge("n_start", "n_seed"));
    edges.add(edge("n_seed", "n_custom"));
    edges.add(edge("n_custom", "n_end"));

    Map<String, Object> definition = new LinkedHashMap<>();
    definition.put("schemaVersion", "1");
    definition.put("name", "黄金自定义 AI 工作流");
    definition.put("description", "含 custom_ai 节点");
    definition.put("scene", "course_content_generation");
    definition.put("modeId", "course-system");
    definition.put("nodes", nodes);
    definition.put("edges", edges);
    return definition;
  }

  private static Map<String, Object> node(
      String key, String typeKey, String name, int sequenceNo, Map<String, Object> config) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("key", key);
    row.put("typeKey", typeKey);
    row.put("name", name);
    row.put("description", "");
    row.put("stage", "main");
    row.put("sequenceNo", sequenceNo);
    row.put("enabled", true);
    row.put("config", config);
    row.put("position", Map.of("x", sequenceNo * 40, "y", 0));
    row.put("skillBindings", List.of());
    return row;
  }

  private static Map<String, Object> edge(String source, String target) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", source + "-" + target);
    row.put("source", source);
    row.put("sourcePort", "output");
    row.put("target", target);
    row.put("targetPort", "input");
    row.put("loop", false);
    return row;
  }

  private static List<Map<String, Object>> slimRuns(List<Map<String, Object>> runs) {
    List<Map<String, Object>> out = new ArrayList<>();
    if (runs == null) {
      return out;
    }
    for (Map<String, Object> run : runs) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("status", run.get("status"));
      row.put("triggerType", run.get("triggerType"));
      out.add(row);
    }
    return out;
  }

  @Override
  public String toString() {
    return id();
  }
}
