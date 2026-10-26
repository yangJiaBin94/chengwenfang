package com.workbench.app.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.workbench.app.support.WorkbenchIT;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "workbench.worker-enabled=true",
    "workbench.worker-concurrency=2"
})
class WorkflowTemplatesAlertsIT extends WorkbenchIT {

  @Test
  void node_types_include_catalog() throws Exception {
    List<Map<String, Object>> nodeTypes = getJsonList("/api/workflows/node-types");
    assertThat(nodeTypes).extracting(m -> m.get("key"))
        .contains("draft_opening", "controlled_condition", "bounded_loop", "commit_version",
            "hot_news_generate_draft");

  }
}
