package com.chengwenfang.app.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.chengwenfang.app.support.FormalAiWireMock;
import com.chengwenfang.app.support.ChengwenfangIT;
import com.chengwenfang.job.facade.constant.JobTypes;
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
class CoursePipelineE2EIT extends ChengwenfangIT {

  private static WireMockServer wireMock;

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
  void ensurePublishedCourseWorkflow() throws Exception {
    FormalAiWireMock.stubDefaults(wireMock);
    pointFormalAiProviders(wireMock);

    List<Map<String, Object>> workflows = getJsonList("/api/workflows");
    String workflowId = null;
    for (Map<String, Object> wf : workflows) {
      if ("course-content-generation".equals(String.valueOf(wf.get("key")))) {
        workflowId = String.valueOf(wf.get("id"));
        break;
      }
    }
    if (workflowId == null) {
      Map<String, Object> created = postJson("/api/workflows", Map.of(
          "name", "Course content generation",
          "key", "course-content-generation",
          "scene", "course_content_generation"));
      workflowId = String.valueOf(created.get("id"));
      workflows = getJsonList("/api/workflows");
    }
    Object published = null;
    for (Map<String, Object> wf : workflows) {
      if (workflowId.equals(String.valueOf(wf.get("id")))) {
        published = wf.get("publishedVersionId");
        break;
      }
    }
    if (published != null && !String.valueOf(published).isBlank() && !"null".equals(String.valueOf(published))) {
      return;
    }
    Map<String, Object> draft = getJson("/api/workflows/" + workflowId + "/draft");
    putJson("/api/workflows/" + workflowId + "/draft", Map.of(
        "revision", draft.get("revision"),
        "definition", minimalCourseDefinition()));
    postJson("/api/workflows/" + workflowId + "/publish", Map.of(
        "version", "e2e-" + UUID.randomUUID().toString().substring(0, 8)));
  }

  @Test
  void course_content_generation_runs_all_stub_steps() throws Exception {
    String courseId = UUID.randomUUID().toString();
    Map<String, Object> started = postJson(
        "/api/courses/" + courseId + "/generate",
        Map.of(
            "type", JobTypes.COURSE_CONTENT_GENERATION,
            "wordCountOption", "2000-4000",
            "imageCountOption", "up-to-3"));
    String jobId = String.valueOf(started.get("jobId"));

    Map<String, Object> terminal = awaitTerminal(jobId, 90);
    assertThat(terminal.get("status")).isEqualTo("succeeded");
    assertThat(terminal.get("progress")).isEqualTo(100);

    List<Map<String, Object>> events = eventHistory(jobId);
    assertThat(events).extracting(e -> e.get("eventType"))
        .contains("queued", "started", "progress", "succeeded");
    assertThat(events).extracting(e -> e.get("step"))
        .contains(
            "draft_opening",
            "draft_core",
            "generate_wechat",
            "generate_images",
            "final_validation",
            "finalize");

    @SuppressWarnings("unchecked")
    Map<String, Object> output = (Map<String, Object>) terminal.get("output");
    assertThat(output.get("courseId")).isEqualTo(courseId);
    assertThat(output.get("partial")).isEqualTo(false);
    assertThat(output.get("draft")).isInstanceOf(Map.class);
    assertThat(output.get("wechat")).isInstanceOf(Map.class);

    Map<String, Object> content = getJson("/api/courses/" + courseId + "/content");
    assertThat(content.get("draft").toString()).contains("本课小结").contains("下节课预告");
    assertThat(content.get("article").toString()).contains("/api/images/");
  }

  @Test
  void course_draft_then_wechat_jobs_complete() throws Exception {
    String courseId = UUID.randomUUID().toString();

    Map<String, Object> draftStarted = postJson(
        "/api/courses/" + courseId + "/generate",
        Map.of("type", JobTypes.COURSE_DRAFT_GENERATION));
    String draftJobId = String.valueOf(draftStarted.get("jobId"));
    assertThat(awaitTerminal(draftJobId).get("status")).isEqualTo("succeeded");
    assertThat(eventHistory(draftJobId)).extracting(e -> e.get("step")).contains("draft");

    Map<String, Object> wechatStarted = postJson(
        "/api/courses/" + courseId + "/generate",
        Map.of("type", JobTypes.WECHAT_ARTICLE_GENERATION));
    String wechatJobId = String.valueOf(wechatStarted.get("jobId"));
    assertThat(awaitTerminal(wechatJobId).get("status")).isEqualTo("succeeded");
    assertThat(eventHistory(wechatJobId)).extracting(e -> e.get("step")).contains("wechat");
  }

  @Test
  void generate_content_endpoint_returns_course_id_in_output() throws Exception {
    String courseId = UUID.randomUUID().toString();
    Map<String, Object> started = postJson(
        "/api/courses/" + courseId + "/generate-content",
        Map.of("imageCountOption", "up-to-3"));
    String jobId = String.valueOf(started.get("id") != null ? started.get("id") : started.get("jobId"));
    Map<String, Object> terminal = awaitTerminal(jobId, 90);
    assertThat(terminal.get("status")).isEqualTo("succeeded");
    @SuppressWarnings("unchecked")
    Map<String, Object> output = (Map<String, Object>) terminal.get("output");
    assertThat(output.get("courseId")).isEqualTo(courseId);
  }

  @Test
  void courses_list_endpoint_responds() throws Exception {
    Map<String, Object> data = getJson("/api/courses");
    assertThat(data).containsKeys("workspace", "courses", "metrics", "generatedAt");
  }

  private static Map<String, Object> minimalCourseDefinition() {
    List<Map<String, Object>> nodes = new ArrayList<>();
    nodes.add(node("n_start", "start", "Start", 0));
    nodes.add(node("n_open", "draft_opening", "Opening", 1));
    nodes.add(node("n_core", "draft_core", "Core", 2));
    nodes.add(node("n_ex", "draft_example", "Example", 3));
    nodes.add(node("n_sum", "draft_summary", "Summary", 4));
    nodes.add(node("n_prev", "draft_preview", "Preview", 5));
    nodes.add(node("n_wechat", "wechat_conversion", "Wechat", 6));
    nodes.add(node("n_cover", "image_cover", "Cover", 7));
    nodes.add(node("n_end", "end", "End", 8));
    Map<String, Object> def = new LinkedHashMap<>();
    def.put("schemaVersion", "1");
    def.put("name", "Course content generation");
    def.put("description", "");
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
        edge("n_cover", "n_end")));
    return def;
  }

  private static Map<String, Object> node(String key, String typeKey, String name, int seq) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("key", key);
    m.put("typeKey", typeKey);
    m.put("name", name);
    m.put("enabled", true);
    m.put("sequenceNo", seq);
    m.put("config", Map.of());
    m.put("skillBindings", List.of());
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
