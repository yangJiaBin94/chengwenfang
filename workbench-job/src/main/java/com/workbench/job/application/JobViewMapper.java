package com.workbench.job.application;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workbench.common.JacksonJsonSupport;
import com.workbench.job.domain.entity.JobEntity;
import com.workbench.job.domain.entity.JobEventEntity;
import com.workbench.job.facade.dto.view.JobEventView;
import com.workbench.job.facade.dto.view.JobView;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Job 实体与对外视图转换。 */
@Component
public class JobViewMapper {
  private static final JacksonJsonSupport JSON = new JacksonJsonSupport(new ObjectMapper());

  /**
   * 实体转 API 视图。
   *
   * @param job 任务实体
   * @return 任务视图；入参为空时返回 null
   */
  public JobView toView(JobEntity job) {
    if (job == null) {
      return null;
    }
    Map<String, Object> input = jsonToMap(job.getInputJson());
    return JobView.builder()
        .id(job.getId())
        .type(job.getJobType())
        .status(job.getStatus())
        .entityType(job.getEntityType())
        .entityId(job.getEntityId())
        .documentId(stringFromMap(input, "documentId"))
        .draftDocumentId(stringFromMap(input, "draftDocumentId"))
        .progress(nullToZero(job.getProgress()))
        .currentStep(job.getCurrentStep())
        .workflowRunId(extractWorkflowRunId(job.getOutputJson()))
        .output(jsonToMap(job.getOutputJson()))
        .errorCode(job.getErrorCode())
        .errorMessage(job.getErrorMessage())
        .retryCount(nullToZero(job.getRetryCount()))
        .createdAt(job.getCreatedAt())
        .startedAt(job.getStartedAt())
        .finishedAt(job.getFinishedAt())
        .build();
  }

  /**
   * 从 Map 取非空白字符串字段。
   *
   * @param map 可空 map
   * @param key 键
   * @return 字符串；缺失为 null
   */
  private static String stringFromMap(Map<String, Object> map, String key) {
    if (map == null || StrUtil.isBlank(key) || !map.containsKey(key) || map.get(key) == null) {
      return null;
    }
    String value = String.valueOf(map.get(key)).trim();
    return StrUtil.isBlank(value) || "null".equalsIgnoreCase(value) ? null : value;
  }

  /**
   * 事件实体转视图。
   *
   * @param event 事件实体
   * @return 事件视图；入参为空时返回 null
   */
  public JobEventView toEventView(JobEventEntity event) {
    if (event == null) {
      return null;
    }
    return JobEventView.builder()
        .id(event.getId() == null ? 0L : event.getId())
        .eventType(event.getEventType())
        .step(event.getStep())
        .progress(nullToZero(event.getProgress()))
        .message(event.getMessage())
        .payload(jsonToMap(event.getPayloadJson()))
        .createdAt(event.getCreatedAt())
        .build();
  }

  /**
   * null Integer 转 0。
   *
   * @param value 可空整数
   * @return 非空整数
   */
  private static int nullToZero(Integer value) {
    return value == null ? 0 : value;
  }

  /**
   * JSON 字符串反序列化为 Map（供 Facade 组装 progressMeta 等）。
   *
   * @param json JSON 文本
   * @return Map；空白为 null
   */
  public Map<String, Object> jsonToMap(String json) {
    return parseJsonMap(json);
  }

  /**
   * 从 Job output JSON 提取工作流运行 ID（runId / workflowRunId）。
   *
   * @param outputJson 输出 JSON
   * @return 运行 ID；无则 null
   */
  private static String extractWorkflowRunId(String outputJson) {
    Map<String, Object> output = parseJsonMap(outputJson);
    if (output == null || output.isEmpty()) {
      return null;
    }
    Object runId = output.get("runId");
    if (runId == null) {
      runId = output.get("workflowRunId");
    }
    String value = runId == null ? null : String.valueOf(runId).trim();
    return StrUtil.isBlank(value) || "null".equalsIgnoreCase(value) ? null : value;
  }

  /**
   * JSON 字符串反序列化为 Map。
   *
   * @param json JSON 文本
   * @return Map；空白为 null
   */
  private static Map<String, Object> parseJsonMap(String json) {
    return JSON.readMapOrRaw(json);
  }
}
