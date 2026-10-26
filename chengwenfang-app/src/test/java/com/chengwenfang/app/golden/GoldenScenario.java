package com.chengwenfang.app.golden;

import com.chengwenfang.app.golden.driver.ScenarioDriver;
import java.util.Map;

/**
 * 黄金场景：准备 → 执行 → 采集产物。
 */
public interface GoldenScenario {
  /**
   * 场景 ID（如 G01）；对应 {@code golden/baseline/<id>/}。
   *
   * @return 非空 ID
   */
  String id();

  /**
   * 准备数据与桩（空库、导入种子、配置 AI 指向 WireMock 等）。
   *
   * @param driver 场景驱动
   * @param context 场景上下文（可变）
   */
  void prepare(ScenarioDriver driver, GoldenScenarioContext context);

  /**
   * 执行业务步骤。
   *
   * @param driver 场景驱动
   * @param context 场景上下文
   */
  void execute(ScenarioDriver driver, GoldenScenarioContext context);

  /**
   * 采集中立产物（尚未归一化）。
   *
   * @param driver 场景驱动
   * @param context 场景上下文
   * @return 键 ai / external / events / outputs → 原文
   */
  Map<String, String> collect(ScenarioDriver driver, GoldenScenarioContext context);

  /**
   * 是否需要 HTTP API 与 Spring 上下文（false 时用 NoOp 驱动，如 G00）。
   *
   * @return 默认 true
   */
  default boolean requiresHttp() {
    return true;
  }
}
