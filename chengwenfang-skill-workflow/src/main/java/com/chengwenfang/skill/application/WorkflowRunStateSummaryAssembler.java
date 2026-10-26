package com.chengwenfang.skill.application;

import cn.hutool.core.util.StrUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 组装工作流运行视图的 stateSummary，键序与原 {@code Map.of("executed", …, "error", …)} 一致。
 */
public final class WorkflowRunStateSummaryAssembler {
  /** 已执行节点列表键。 */
  public static final String KEY_EXECUTED = "executed";
  /** 错误信息键。 */
  public static final String KEY_ERROR = "error";

  private WorkflowRunStateSummaryAssembler() {}

  /**
   * 组装 stateSummary。
   *
   * @param executedNodeKeys 已执行节点键（按 seq 升序）
   * @param errorMessage     失败信息；null 按空串
   * @return LinkedHashMap，键序 executed → error
   */
  public static Map<String, Object> assemble(List<String> executedNodeKeys, String errorMessage) {
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put(KEY_EXECUTED, executedNodeKeys == null ? List.of() : List.copyOf(executedNodeKeys));
    summary.put(KEY_ERROR, errorMessage == null ? "" : errorMessage);
    return summary;
  }

  /**
   * 从图定义 Map 解析 schema_version 整数列值。
   *
   * @param definition 图定义；可空
   * @return 正整数；缺省或非法时为 1
   */
  public static int schemaVersionOf(Map<String, Object> definition) {
    if (definition == null || definition.isEmpty()) {
      return 1;
    }
    Object raw = definition.get("schemaVersion");
    if (raw == null) {
      return 1;
    }
    if (raw instanceof Number number) {
      int value = number.intValue();
      return value > 0 ? value : 1;
    }
    String text = StrUtil.trim(String.valueOf(raw));
    if (StrUtil.isBlank(text)) {
      return 1;
    }
    try {
      int value = Integer.parseInt(text);
      return value > 0 ? value : 1;
    } catch (NumberFormatException ignored) {
      return 1;
    }
  }

  /**
   * 复制已执行节点列表（可变 ArrayList，供测试断言）。
   *
   * @param executed 源列表
   * @return 新列表
   */
  public static List<String> copyExecuted(List<String> executed) {
    return executed == null ? new ArrayList<>() : new ArrayList<>(executed);
  }
}
