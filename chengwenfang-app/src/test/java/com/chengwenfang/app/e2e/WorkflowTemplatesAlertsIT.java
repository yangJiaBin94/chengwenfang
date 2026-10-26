package com.chengwenfang.app.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.app.support.ChengwenfangIT;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "chengwenfang.worker-enabled=true",
    "chengwenfang.worker-concurrency=2"
})
class WorkflowTemplatesAlertsIT extends ChengwenfangIT {

  @Test
  void node_types_include_catalog() throws Exception {
    List<Map<String, Object>> nodeTypes = getJsonList("/api/workflows/node-types");
    assertThat(nodeTypes).extracting(m -> m.get("key"))
        .contains("draft_opening", "controlled_condition", "bounded_loop", "commit_version",
            "hot_news_generate_draft");

  }
}
