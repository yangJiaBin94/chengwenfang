package com.chengwenfang.app.golden.scenario;

import com.chengwenfang.app.golden.GoldenScenario;
import com.chengwenfang.app.golden.GoldenScenarioContext;
import com.chengwenfang.app.golden.driver.ScenarioDriver;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 空场景 G00：验证黄金框架录制/比对链路可运行，无业务副作用。
 */
public final class EmptyGoldenScenario implements GoldenScenario {
  /** 场景 ID。 */
  public static final String ID = "G00";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public void prepare(ScenarioDriver driver, GoldenScenarioContext context) {
    driver.reset(context);
  }

  @Override
  public void execute(ScenarioDriver driver, GoldenScenarioContext context) {
    // 无业务步骤
  }

  @Override
  public Map<String, String> collect(ScenarioDriver driver, GoldenScenarioContext context) {
    Map<String, String> artifacts = new LinkedHashMap<>();
    artifacts.put("ai", "");
    artifacts.put("external", "");
    artifacts.put("events", "");
    artifacts.put("outputs", "{}");
    return artifacts;
  }

  @Override
  public boolean requiresHttp() {
    return false;
  }

  @Override
  public String toString() {
    return id();
  }
}
