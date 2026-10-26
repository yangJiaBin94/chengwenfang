package com.chengwenfang.app.golden.driver;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link ApiV1ScenarioDriver} 轻量单测（不启 Spring）：jobId 解析与产物打包。
 */
class ApiV1ScenarioDriverTest {
  @Test
  void extractJobId_prefers_id_then_jobId() {
    assertThat(ApiV1ScenarioDriver.extractJobId(Map.of("id", "j1"))).isEqualTo("j1");
    assertThat(ApiV1ScenarioDriver.extractJobId(Map.of("jobId", "j2"))).isEqualTo("j2");
  }

  @Test
  void packArtifacts_includes_four_keys() {
    ApiV1ScenarioDriver driver = new ApiV1ScenarioDriver(0, new ObjectMapper(), null);
    Map<String, String> artifacts = driver.packArtifacts("{\"event\":\"x\"}\n", Map.of("ok", true));
    assertThat(artifacts.keySet()).containsExactly("ai", "external", "events", "outputs");
    assertThat(artifacts.get("events")).contains("event");
    assertThat(artifacts.get("outputs")).contains("ok");
  }
}
