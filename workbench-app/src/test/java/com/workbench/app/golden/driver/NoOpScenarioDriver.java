package com.workbench.app.golden.driver;

import com.workbench.app.golden.GoldenScenarioContext;
import java.util.List;
import java.util.Map;

/**
 * 空驱动：供骨架空场景与单测使用，不访问 HTTP。
 */
public final class NoOpScenarioDriver implements ScenarioDriver {
  @Override
  public String name() {
    return "noop";
  }

  @Override
  public void reset(GoldenScenarioContext context) {
    // 无操作
  }

  @Override
  public List<Map<String, Object>> jobEventHistory(String jobId) {
    return List.of();
  }
}
