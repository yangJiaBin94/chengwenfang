package com.chengwenfang.app.golden.record;

import cn.hutool.json.JSONUtil;
import com.chengwenfang.app.golden.driver.ScenarioDriver;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通过场景驱动读取任务事件历史并序列化为 jsonl（去掉自增 id 等易变字段）。
 */
public final class JobEventRecorder {
  private final ScenarioDriver driver;

  /**
   * @param driver 场景驱动
   */
  public JobEventRecorder(ScenarioDriver driver) {
    this.driver = driver;
  }

  /**
   * 导出指定任务的事件 jsonl。
   *
   * @param jobId 任务 ID
   * @return 每行一个事件 JSON；无事件为空串
   */
  public String exportJsonl(String jobId) {
    List<Map<String, Object>> events = driver.jobEventHistory(jobId);
    if (events == null || events.isEmpty()) {
      return "";
    }
    StringBuilder builder = new StringBuilder();
    int sequence = 1;
    for (Map<String, Object> event : events) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("seq", sequence++);
      row.put("eventType", event.get("eventType"));
      row.put("step", event.get("step"));
      row.put("progress", event.get("progress"));
      row.put("message", event.get("message"));
      builder.append(JSONUtil.toJsonStr(row)).append('\n');
    }
    return builder.toString();
  }
}
