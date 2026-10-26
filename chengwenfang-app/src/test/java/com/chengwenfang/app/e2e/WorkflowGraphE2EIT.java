package com.chengwenfang.app.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.app.support.ChengwenfangIT;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "chengwenfang.worker-enabled=true",
    "chengwenfang.worker-concurrency=2"
})
class WorkflowGraphE2EIT extends ChengwenfangIT {

  @Test
  void formal_graph_runs_condition_and_persists_node_runs() throws Exception {
    Map<String, Object> created = postJson("/api/workflows", Map.of(
        "name", "Graph IT",
        "scene", "course_content_generation"));
    String id = String.valueOf(created.get("id"));

    Map<String, Object> definition = Map.of(
        "schemaVersion", "1",
        "name", "Graph IT",
        "description", "",
        "scene", "course_content_generation",
        "modeId", "course-system",
        "nodes", List.of(
            node("n_start", "start", "Start", 0),
            node("n_draft", "draft_opening", "Opening", 1),
            node("n_cond", "controlled_condition", "Cond", 2),
            node("n_ok", "draft_summary", "Summary", 3),
            node("n_end", "end", "End", 4)
        ),
        "edges", List.of(
            edge("n_start", "n_draft", null, false),
            edge("n_draft", "n_cond", null, false),
            edge("n_cond", "n_ok", Map.of("field", "reviewPassed", "operator", "eq", "value", true), false),
            edge("n_ok", "n_end", null, false)
        ));

    putJson("/api/workflows/" + id + "/draft", Map.of(
        "revision", getJson("/api/workflows/" + id + "/draft").get("revision"),
        "definition", definition));
    postJson("/api/workflows/" + id + "/publish", Map.of("note", "graph-e2e"));

    String courseId = UUID.randomUUID().toString();
    Map<String, Object> started = postJson(
        "/api/courses/" + courseId + "/generate-content",
        Map.of("workflowId", id, "wordCountOption", "2000-4000", "imageCountOption", "up-to-3"));
    String jobId = String.valueOf(started.get("id") != null ? started.get("id") : started.get("jobId"));
    Map<String, Object> terminal = awaitTerminal(jobId, 90);
    assertThat(terminal.get("status")).isEqualTo("succeeded");

    List<Map<String, Object>> runs = getJsonList("/api/workflows/" + id + "/runs");
    assertThat(runs).isNotEmpty();

    Integer nodeRuns = jdbc.queryForObject(
        "SELECT COUNT(*) FROM workflow_node_runs nr "
            + "JOIN workflow_runs wr ON wr.id = nr.workflow_run_id "
            + "WHERE wr.workflow_definition_id = ?",
        Integer.class,
        id);
    assertThat(nodeRuns).isGreaterThanOrEqualTo(3);

    Map<String, Object> metrics = getJson("/api/workflows/" + id + "/metrics/summary");
    assertThat(((Number) metrics.get("totalRuns")).intValue()).isGreaterThanOrEqualTo(1);
  }

  private static Map<String, Object> node(String key, String typeKey, String name, int seq) {
    return Map.of(
        "key", key,
        "typeKey", typeKey,
        "name", name,
        "description", "",
        "stage", "main",
        "sequenceNo", seq,
        "enabled", true,
        "config", Map.of(
            "timeoutSeconds", 60,
            "maxAttempts", 1,
            "failurePolicy", "stop",
            "conditionField", "reviewPassed",
            "conditionOperator", "eq",
            "conditionValue", true),
        "position", Map.of("x", seq * 40, "y", 0),
        "skillBindings", List.of());
  }

  private static Map<String, Object> edge(
      String source, String target, Map<String, Object> condition, boolean loop) {
    Map<String, Object> m = new java.util.LinkedHashMap<>();
    m.put("id", source + "-" + target);
    m.put("source", source);
    m.put("sourcePort", "output");
    m.put("target", target);
    m.put("targetPort", "input");
    m.put("loop", loop);
    if (condition != null) {
      m.put("condition", condition);
    }
    return m;
  }
}
