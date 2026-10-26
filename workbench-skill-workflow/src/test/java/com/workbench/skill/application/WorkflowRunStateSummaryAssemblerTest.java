package com.workbench.skill.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** stateSummary 组装与原 Map.of 键序/空 error 对齐。 */
class WorkflowRunStateSummaryAssemblerTest {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void assemble_matchesMapOfKeyOrderAndEmptyError() throws Exception {
    List<String> executed = List.of("start", "draft_core");
    Map<String, Object> expected = new LinkedHashMap<>();
    expected.put("executed", executed);
    expected.put("error", "");
    Map<String, Object> actual = WorkflowRunStateSummaryAssembler.assemble(executed, null);
    assertThat(objectMapper.writeValueAsString(actual))
        .isEqualTo(objectMapper.writeValueAsString(expected));
    assertThat(actual.keySet()).containsExactly("executed", "error");
  }

  @Test
  void assemble_keepsNonEmptyError() throws Exception {
    Map<String, Object> actual =
        WorkflowRunStateSummaryAssembler.assemble(List.of("n1"), "node failed");
    assertThat(actual.get("error")).isEqualTo("node failed");
    assertThat(objectMapper.writeValueAsString(actual))
        .contains("\"executed\"")
        .contains("\"error\"");
  }

  @Test
  void schemaVersionOf_defaultsAndParses() {
    assertThat(WorkflowRunStateSummaryAssembler.schemaVersionOf(null)).isEqualTo(1);
    assertThat(WorkflowRunStateSummaryAssembler.schemaVersionOf(Map.of())).isEqualTo(1);
    assertThat(WorkflowRunStateSummaryAssembler.schemaVersionOf(Map.of("schemaVersion", "2")))
        .isEqualTo(2);
    assertThat(WorkflowRunStateSummaryAssembler.schemaVersionOf(Map.of("schemaVersion", 3)))
        .isEqualTo(3);
  }
}
