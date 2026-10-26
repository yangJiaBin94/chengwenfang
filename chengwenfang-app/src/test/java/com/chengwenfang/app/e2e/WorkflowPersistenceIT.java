package com.chengwenfang.app.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.app.support.ChengwenfangIT;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkflowPersistenceIT extends ChengwenfangIT {

  @Test
  void create_save_draft_publish_persists_published_version() throws Exception {
    Map<String, Object> created = postJson(
        "/api/workflows",
        Map.of("name", "Persist workflow", "description", "n1 persistence"));
    assertThat(created.get("id")).isNotNull();
    String id = String.valueOf(created.get("id"));

    Map<String, Object> draft = getJson("/api/workflows/" + id + "/draft");
    assertThat(draft.get("revision")).isEqualTo(1);

    Map<String, Object> definition = Map.of(
        "schemaVersion", "1",
        "name", "Persist workflow",
        "description", "n1 persistence",
        "scene", "course_content_generation",
        "modeId", "",
        "nodes", List.of(
            Map.of("key", "start", "typeKey", "start", "name", "Start"),
            Map.of("key", "end", "typeKey", "end", "name", "End")),
        "edges", List.of(
            Map.of("id", "e1", "source", "start", "target", "end")));

    Map<String, Object> saved = putJson(
        "/api/workflows/" + id + "/draft",
        Map.of("revision", 1, "definition", definition));
    assertThat(saved.get("revision")).isEqualTo(2);
    assertThat(saved.get("definition")).isInstanceOf(Map.class);

    Map<String, Object> published = postJson(
        "/api/workflows/" + id + "/publish",
        Map.of("releaseNotes", "first publish"));
    assertThat(published.get("id")).isNotNull();
    assertThat(published.get("version")).isEqualTo("1");
    assertThat(published.get("publishedVersionId")).isEqualTo(published.get("id"));

    Map<String, Object> detail = getJson("/api/workflows/" + id);
    assertThat(detail.get("publishedVersionId")).isEqualTo(published.get("id"));
    assertThat(detail.get("publishedVersion")).isEqualTo("1");
    assertThat(detail.get("status")).isEqualTo("inactive");
  }
}
