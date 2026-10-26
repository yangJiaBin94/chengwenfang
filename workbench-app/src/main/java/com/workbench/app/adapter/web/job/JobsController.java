package com.workbench.app.adapter.web.job;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import com.workbench.common.ApiResult;
import com.workbench.infra.config.WorkbenchProperties;
import com.workbench.job.facade.dto.view.JobEventView;
import com.workbench.job.facade.JobFacade;
import com.workbench.job.facade.dto.view.JobResultView;
import com.workbench.job.facade.enums.JobStatus;
import com.workbench.job.facade.dto.view.JobView;
import com.workbench.skill.facade.WorkflowFacade;
import com.workbench.skill.facade.dto.view.WorkflowNodeRunView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 后台 Job 查询、取消与 SSE 事件流接口。 */
@Tag(name = "任务", description = "异步 Job 查询、取消与事件流")
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class JobsController {
  /** 任务门面：查询、取消、重试与事件。 */
  private final JobFacade jobFacade;
  /** 工作流门面：按 Job 查节点运行。 */
  private final WorkflowFacade workflows;
  /** 配置项：SSE 轮询间隔等。 */
  private final WorkbenchProperties properties;

  /**
   * 查询任务详情。
   *
   * @param jobId 任务主键
   * @return 包装后的任务视图
   */
  @Operation(summary = "查询任务详情")
  @GetMapping("/jobs/{jobId}")
  public ApiResult<JobView> get(@PathVariable String jobId) {
    return ApiResult.ok(jobFacade.get(jobId));
  }

  /**
   * 查询任务结果（状态与 output）。
   *
   * @param jobId 任务主键
   * @return 含 status 与 output 的结果视图
   */
  @Operation(summary = "查询任务结果")
  @GetMapping("/jobs/{jobId}/result")
  public ApiResult<JobResultView> result(@PathVariable String jobId) {
    JobView job = jobFacade.get(jobId);
    return ApiResult.ok(JobResultView.builder()
        .status(job.getStatus())
        .output(ObjectUtil.defaultIfNull(job.getOutput(), Map.of()))
        .build());
  }

  /**
   * 取消非终态任务。
   *
   * @param jobId 任务主键
   * @return 取消后的任务视图
   */
  @Operation(summary = "取消任务")
  @PostMapping("/jobs/{jobId}/cancel")
  public ApiResult<JobView> cancel(@PathVariable String jobId) {
    return ApiResult.ok(jobFacade.cancel(jobId));
  }

  /**
   * 将 failed 任务重新排队。
   *
   * @param jobId 任务主键
   * @return HTTP 202 + 重新入队后的任务视图
   */
  @Operation(summary = "重试失败任务")
  @PostMapping("/jobs/{jobId}/retry-failed")
  public ResponseEntity<ApiResult<JobView>> retry(@PathVariable String jobId) {
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(ApiResult.ok(jobFacade.retryFailed(jobId)));
  }

  /**
   * 返回任务自创建起的全部事件历史。
   *
   * @param jobId 任务主键
   * @return 事件视图列表（afterId=0）
   */
  @Operation(summary = "任务事件历史")
  @GetMapping("/jobs/{jobId}/event-history")
  public ApiResult<List<JobEventView>> history(@PathVariable String jobId) {
    return ApiResult.ok(jobFacade.eventsAfter(jobId, 0));
  }

  /**
   * 课程执行轨迹页查询任务节点尝试（对齐原版：从 workflow_node_runs 读取）。
   *
   * @param jobId 任务主键
   * @return 节点运行列表
   */
  @Operation(summary = "任务节点尝试")
  @GetMapping("/jobs/{jobId}/nodes")
  public ApiResult<List<WorkflowNodeRunView>> nodes(@PathVariable String jobId) {
    jobFacade.get(jobId);
    return ApiResult.ok(workflows.listNodeRunsByJobId(jobId));
  }

  /**
   * 课程执行轨迹页查询任务关联文章版本；当前未按 job 落库，返回空列表避免 404。
   *
   * @param jobId 任务主键
   * @return 空版本列表
   */
  @Operation(summary = "任务文章版本（占位）")
  @GetMapping("/jobs/{jobId}/article-versions")
  public ApiResult<List<Object>> articleVersions(@PathVariable String jobId) {
    jobFacade.get(jobId);
    return ApiResult.ok(List.of());
  }

  /**
   * SSE 推送任务事件流，支持 Last-Event-ID/after 续传。
   * <p>SSE 载荷保持事件 JSON，不包 ApiResult。
   *
   * @param jobId       任务主键
   * @param lastEventId 标准 SSE 续传头；与 after 二选一，after 优先
   * @param after       查询参数形式的游标；指定时覆盖 Last-Event-ID
   * @return 永不超时的 {@link SseEmitter}；实际推送在虚拟线程中进行
   */
  @Operation(summary = "任务事件 SSE 流")
  @GetMapping(path = "/jobs/{jobId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter events(
      @PathVariable String jobId,
      @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
      @RequestParam(value = "after", required = false) Long after) {
    // after 优先；否则解析 Last-Event-ID，缺省从 0 开始
    long cursor = after != null ? after : Convert.toLong(lastEventId, 0L);
    SseEmitter emitter = new SseEmitter(0L);
    Thread.startVirtualThread(() -> stream(jobId, cursor, emitter));
    return emitter;
  }

  /**
   * 轮询事件并推送，直到任务终态或发生错误。
   *
   * @param jobId   任务主键
   * @param cursor  已消费的最大事件 id
   * @param emitter SSE 发射器
   */
  private void stream(String jobId, long cursor, SseEmitter emitter) {
    long current = cursor;
    try {
      while (true) {
        List<JobEventView> batch = jobFacade.eventsAfter(jobId, current);
        for (JobEventView event : batch) {
          current = event.getId();
          emitter.send(SseEmitter.event()
              .id(String.valueOf(event.getId()))
              .name(event.getEventType())
              .data(JSONUtil.toJsonStr(event)));
        }
        JobView job = jobFacade.get(jobId);
        // 无新事件且任务已终态：正常结束流
        if (batch.isEmpty() && JobStatus.from(job.getStatus()).isTerminal()) {
          log.debug("SSE 完成 jobId={} status={}", jobId, job.getStatus());
          emitter.complete();
          return;
        }
        TimeUnit.MILLISECONDS.sleep((long) (properties.getSsePollSeconds() * 1000));
      }
    } catch (Exception exception) {
      if (isClientDisconnected(exception)) {
        log.debug("SSE 客户端断开 jobId={}", jobId);
        quietlyComplete(emitter);
        return;
      }
      log.warn("SSE 流失败 jobId={} err={}", jobId, exception.getMessage());
      try {
        emitter.completeWithError(exception);
      } catch (Exception ignored) {
        quietlyComplete(emitter);
      }
    }
  }

  /** 浏览器关掉 EventSource / 刷新页面时的正常断开，勿 completeWithError。 */
  private static boolean isClientDisconnected(Throwable error) {
    Throwable current = error;
    while (current != null) {
      String name = current.getClass().getName();
      if (name.contains("ClientAbortException")
          || name.contains("AsyncRequestNotUsableException")) {
        return true;
      }
      String message = current.getMessage();
      if (message != null
          && (message.contains("Broken pipe")
              || message.contains("Connection reset")
              || message.contains("中止了一个已建立的连接")
              || message.contains("你的主机中的软件中止"))) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private static void quietlyComplete(SseEmitter emitter) {
    try {
      emitter.complete();
    } catch (Exception ignored) {
      // 连接已断，忽略
    }
  }
}
