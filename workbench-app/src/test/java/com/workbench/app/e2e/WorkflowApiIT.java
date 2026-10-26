package com.workbench.app.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.workbench.app.support.WorkbenchIT;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkflowApiIT extends WorkbenchIT {

  @Test
  void workflows_modes_and_publishing_themes_smoke() throws Exception {
    List<Map<String, Object>> modes = getJsonList("/api/modes");
    assertThat(modes).isNotNull();

    List<Map<String, Object>> before = getJsonList("/api/workflows");
    assertThat(before).isNotNull();

    Map<String, Object> created = postJson(
        "/api/workflows",
        Map.of("name", "Smoke workflow", "description", "api it"));
    assertThat(created.get("id")).isNotNull();
    assertThat(created.get("name")).isEqualTo("Smoke workflow");

    String id = String.valueOf(created.get("id"));
    Map<String, Object> detail = getJson("/api/workflows/" + id);
    assertThat(detail.get("id")).isEqualTo(id);
    assertThat(detail.get("definition")).isInstanceOf(Map.class);

    Map<String, Object> draft = getJson("/api/workflows/" + id + "/draft");
    assertThat(draft.get("revision")).isNotNull();
    assertThat(draft.get("definition")).isInstanceOf(Map.class);

    List<Map<String, Object>> listed = getJsonList("/api/workflows");
    assertThat(listed).extracting(m -> m.get("id")).contains(id);

    List<Map<String, Object>> themes = getJsonList("/api/layout/themes");
    assertThat(themes).isNotEmpty();
    assertThat(themes.get(0).get("id")).isNotNull();
    assertThat(themes.get(0).get("name")).isNotNull();
  }
}
