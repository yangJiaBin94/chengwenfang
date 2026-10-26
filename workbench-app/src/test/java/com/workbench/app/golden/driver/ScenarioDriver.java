package com.workbench.app.golden.driver;

import com.workbench.app.golden.GoldenScenarioContext;
import java.util.List;
import java.util.Map;

/**
 * 场景驱动：通过 HTTP API（或后续适配层）操作系统并读出产物。
 */
public interface ScenarioDriver {
  /**
   * 驱动标识（如 api-v1）。
   *
   * @return 非空标识
   */
  String name();

  /**
   * 场景开始前重置（清库可选、清 WireMock 日志等）。由具体 IT 注入能力后实现。
   *
   * @param context 场景上下文
   */
  void reset(GoldenScenarioContext context);

  /**
   * 读取任务事件历史（中立结构列表）。
   *
   * @param jobId 任务 ID
   * @return 事件列表；无实现时返回空列表
   */
  List<Map<String, Object>> jobEventHistory(String jobId);
}
