package com.workbench.job.facade;

import com.workbench.infra.config.WorkbenchProperties;
import java.util.Map;

/**
 * Handler 执行上下文：上报进度/事件、续租、检测取消，以及设置完成输出。
 */
public class JobExecutionContext {
  private final JobFacade jobs;
  private final WorkbenchProperties properties;
  private Object completionOutput;

  public JobExecutionContext(JobFacade jobs, WorkbenchProperties properties) {
    this.jobs = jobs;
    this.properties = properties;
  }

  /**
   * 更新任务当前步骤与进度，并追加 progress 事件。
   *
   * @param progress 建议 0–100
   */
  public void progress(String jobId, String step, int progress, String message) {
    jobs.updateProgress(jobId, step, progress, message);
  }

  /**
   * 更新进度并附带结构化元数据（总数、当前条等）。
   *
   * @param progress 建议 0–100
   * @param payload  进度元数据，可空
   */
  public void progress(
      String jobId, String step, int progress, String message, Map<String, Object> payload) {
    jobs.updateProgress(jobId, step, progress, message, payload);
  }

  /** 追加自定义事件（不一定改任务主状态）。 */
  public void event(String jobId, String type, String step, int progress, String message, Map<String, Object> payload) {
    jobs.appendEvent(jobId, type, step, progress, message, payload);
  }

  /** 是否已请求取消（状态为 cancelled）。 */
  public boolean isCancelled(String jobId) {
    return jobs.isCancelRequested(jobId);
  }

  /** 按配置的租约秒数续约，避免长任务被其他 Worker 抢走。 */
  public void renewLease(String jobId) {
    jobs.renewLease(jobId, properties.getWorkerLeaseSeconds());
  }

  /**
   * 设置 Handler 成功结束后写入的输出；未设置时 Worker 使用默认 {@code {ok:true}}。
   *
   * @param output 完成出参（bean / Map / FlexiblePayload）
   */
  public void completeWith(Object output) {
    this.completionOutput = output;
  }

  /**
   * 返回 Handler 通过 {@link #completeWith} 设置的完成输出，可能为 null。
   *
   * @return 完成出参；未设置时为 null
   */
  public Object completionOutput() {
    return completionOutput;
  }

  public WorkbenchProperties properties() {
    return properties;
  }
}
