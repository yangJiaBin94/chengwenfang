package com.chengwenfang.app.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.chengwenfang.app.support.FormalAiWireMock;
import com.chengwenfang.app.support.ChengwenfangIT;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "chengwenfang.worker-enabled=true",
    "chengwenfang.worker-concurrency=2"
})
class FormalCourseGenerationIT extends ChengwenfangIT {

  private static final String SKILL_MARKER = "FORMAL-SKILL-MARKER-IT-ONLY";
  private static WireMockServer wireMock;

  private String workflowId;
  private String skillId;

  @BeforeAll
  static void startWireMock() {
    wireMock = FormalAiWireMock.start();
  }

  @AfterAll
  static void stopWireMock() {
    if (wireMock != null) {
      wireMock.stop();
    }
  }

  @BeforeEach
  void publishFormalWorkflow() throws Exception {
    FormalAiWireMock.stubDefaults(wireMock);
    pointFormalAiProviders(wireMock);

    Map<String, Object> skill = postJson("/api/settings/skills", Map.of(
        "name", "Formal IT Skill",
        "scene", "course_content_generation",
        "systemInstructions", "You are a course writer. " + SKILL_MARKER,
        "taskInstructions", "Keep structure with 本课小结 and 下节课预告.",
        "priority", 10,
        "enabled", true));
    skillId = String.valueOf(skill.get("id"));

    workflowId = findOrCreateCourseWorkflow();
    Map<String, Object> draft = getJson("/api/workflows/" + workflowId + "/draft");
    putJson("/api/workflows/" + workflowId + "/draft", Map.of(
        "revision", draft.get("revision"),
        "definition", formalDefinition(skillId)));
    postJson("/api/workflows/" + workflowId + "/publish", Map.of(
        "version", "it-formal-" + UUID.randomUUID().toString().substring(0, 8),
        "releaseNotes", "formal generation IT"));
  }

  @Test
  void generate_content_enqueues_formal_run_with_workflow() throws Exception {
    String courseId = UUID.randomUUID().toString();
    Map<String, Object> started = postJson(
        "/api/courses/" + courseId + "/generate-content",
        Map.of("wordCountOption", "2000-4000", "imageCountOption", "up-to-3"));
    String jobId = String.valueOf(started.get("id") != null ? started.get("id") : started.get("jobId"));

    String inputJson = jdbc.queryForObject("SELECT input_json FROM jobs WHERE id = ?", String.class, jobId);
    @SuppressWarnings("unchecked")
    Map<String, Object> input = objectMapper.readValue(inputJson, Map.class);
    assertThat(input.get("workflowId")).isEqualTo(workflowId);
    assertThat(input.containsKey("runMode")).isFalse();

    Integer runs = jdbc.queryForObject(
        "SELECT COUNT(*) FROM workflow_runs WHERE job_id = ?",
        Integer.class,
        jobId);
    assertThat(runs).isEqualTo(1);

    Map<String, Object> terminal = awaitTerminal(jobId, 90);
    assertThat(terminal.get("status")).isEqualTo("succeeded");
    assertThat(eventHistory(jobId)).extracting(e -> e.get("step"))
        .contains("draft_opening", "draft_core", "generate_wechat", "generate_images",
            "formula_style_validation", "final_validation", "finalize");

    Integer nodeRuns = jdbc.queryForObject(
        "SELECT COUNT(*) FROM workflow_node_runs nr "
            + "JOIN workflow_runs wr ON wr.id = nr.workflow_run_id WHERE wr.job_id = ?",
        Integer.class,
        jobId);
    assertThat(nodeRuns).isGreaterThanOrEqualTo(5);

    Map<String, Object> content = getJson("/api/courses/" + courseId + "/content");
    assertThat(content.get("draft").toString())
        .contains("本课小结")
        .contains("下节课预告")
        .doesNotContain("请把课程原稿发给我");
    assertThat(content.get("article").toString())
        .contains("/api/images/")
        .doesNotContain("[[COURSE_COVER]]");

    Integer images = jdbc.queryForObject(
        "SELECT COUNT(*) FROM media_assets WHERE work_id = ? AND origin = 'generated'",
        Integer.class,
        courseId);
    assertThat(images).isGreaterThanOrEqualTo(1);
  }

  @Test
  void compose_prompt_from_bindings_includes_skill_text() throws Exception {
    Integer bindingCount = jdbc.queryForObject(
        "SELECT COUNT(*) FROM workflow_version_skill_bindings WHERE skill_id = ?",
        Integer.class,
        skillId);
    assertThat(bindingCount).isGreaterThanOrEqualTo(1);
    String instructions = jdbc.queryForObject(
        "SELECT system_instructions FROM skills WHERE id = ?",
        String.class,
        skillId);
    assertThat(instructions).contains(SKILL_MARKER);
  }

  @Test
  void generate_content_fails_when_no_published_workflow() throws Exception {
    jdbc.update("UPDATE workflow_definitions SET published_version_id = NULL WHERE scene = 'course_content_generation'");
    String courseId = UUID.randomUUID().toString();
    HttpResponse<String> response = http.send(
        HttpRequest.newBuilder(URI.create(baseUrl() + "/api/courses/" + courseId + "/generate-content"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .build(),
        HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isGreaterThanOrEqualTo(400);
    assertThat(response.body()).contains("workflow_not_published");
  }

  private String findOrCreateCourseWorkflow() throws Exception {
    List<Map<String, Object>> workflows = getJsonList("/api/workflows");
    for (Map<String, Object> wf : workflows) {
      if ("course-content-generation".equals(String.valueOf(wf.get("key")))) {
        return String.valueOf(wf.get("id"));
      }
    }
    Map<String, Object> created = postJson("/api/workflows", Map.of(
        "name", "生成课程内容",
        "key", "course-content-generation",
        "scene", "course_content_generation"));
    return String.valueOf(created.get("id"));
  }

  private static Map<String, Object> formalDefinition(String skillId) {
    List<Map<String, Object>> bindings = List.of(Map.of(
        "skillId", skillId,
        "priority", 10,
        "enabled", true));
    List<Map<String, Object>> nodes = new ArrayList<>();
    nodes.add(node("n_start", "start", "Start", 0, List.of()));
    nodes.add(node("n_open", "draft_opening", "Opening", 1, bindings));
    nodes.add(node("n_core", "draft_core", "Core", 2, bindings));
    nodes.add(node("n_ex", "draft_example", "Example", 3, bindings));
    nodes.add(node("n_sum", "draft_summary", "Summary", 4, bindings));
    nodes.add(node("n_prev", "draft_preview", "Preview", 5, bindings));
    nodes.add(node("n_wechat", "wechat_conversion", "Wechat", 6, bindings));
    nodes.add(node("n_cover", "image_cover", "Cover", 7, bindings));
    nodes.add(node("n_inline", "image_inline", "Inline", 8, bindings));
    nodes.add(node("n_end", "end", "End", 9, List.of()));
    Map<String, Object> def = new LinkedHashMap<>();
    def.put("schemaVersion", "1");
    def.put("name", "Course content generation");
    def.put("description", "formal IT");
    def.put("scene", "course_content_generation");
    def.put("modeId", "course-system");
    def.put("nodes", nodes);
    def.put("edges", List.of(
        edge("n_start", "n_open"),
        edge("n_open", "n_core"),
        edge("n_core", "n_ex"),
        edge("n_ex", "n_sum"),
        edge("n_sum", "n_prev"),
        edge("n_prev", "n_wechat"),
        edge("n_wechat", "n_cover"),
        edge("n_cover", "n_inline"),
        edge("n_inline", "n_end")));
    return def;
  }

  private static Map<String, Object> node(
      String key, String typeKey, String name, int seq, List<Map<String, Object>> bindings) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("key", key);
    m.put("typeKey", typeKey);
    m.put("name", name);
    m.put("enabled", true);
    m.put("sequenceNo", seq);
    m.put("config", Map.of("timeoutSeconds", 60, "maxAttempts", 1, "failurePolicy", "stop"));
    m.put("skillBindings", bindings);
    return m;
  }

  private static Map<String, Object> edge(String source, String target) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", source + "-" + target);
    m.put("source", source);
    m.put("target", target);
    m.put("loop", false);
    return m;
  }
}
