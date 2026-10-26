package com.workbench.app.golden;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单次场景运行上下文：运行目录、任务 ID、临时属性。
 */
public final class GoldenScenarioContext {
  /** 场景 ID。 */
  private final String scenarioId;
  /** 本场景独立 runtime 目录。 */
  private Path runtimeDir;
  /** 扩展属性（jobId、themeId 等）。 */
  private final Map<String, Object> attributes = new LinkedHashMap<>();

  /**
   * @param scenarioId 场景 ID
   */
  public GoldenScenarioContext(String scenarioId) {
    this.scenarioId = scenarioId;
  }

  /**
   * @return 场景 ID
   */
  public String scenarioId() {
    return scenarioId;
  }

  /**
   * @return runtime 目录
   */
  public Path runtimeDir() {
    return runtimeDir;
  }

  /**
   * @param runtimeDir runtime 目录
   */
  public void setRuntimeDir(Path runtimeDir) {
    this.runtimeDir = runtimeDir;
  }

  /**
   * 写入属性。
   *
   * @param key   键
   * @param value 值
   */
  public void put(String key, Object value) {
    attributes.put(key, value);
  }

  /**
   * 读取属性。
   *
   * @param key 键
   * @param <T> 类型
   * @return 值；无则 null
   */
  @SuppressWarnings("unchecked")
  public <T> T get(String key) {
    return (T) attributes.get(key);
  }
}
