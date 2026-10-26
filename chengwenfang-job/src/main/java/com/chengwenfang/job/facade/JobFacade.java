package com.chengwenfang.job.facade;


import com.chengwenfang.job.facade.constant.JobTypes;
import com.chengwenfang.job.facade.dto.view.JobEventView;
import com.chengwenfang.job.facade.dto.view.JobTerminalEvent;
import com.chengwenfang.job.facade.dto.view.JobView;
import com.chengwenfang.job.domain.entity.JobEntity;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 异步任务门面：创建/查询/取消/重试，以及 Worker 认领、进度与终态写入。
 */
public interface JobFacade {
  /** 系统支持的任务类型集合。 */
  Set<String> supportedTypes();

  /**
   * 创建 pending 任务并写入 queued 事件。
   *
   * @param type 须在 {@link JobTypes#ALL} 中
   * @param input 序列化为 input_json（bean / Map / FlexiblePayload）；null 视为空对象
   */
  JobView create(String type, String entityType, String entityId, Object input);

  /** 按 id 查询任务视图；不存在时抛业务异常。 */
  JobView get(String jobId);

  /**
   * 按类型列出最近任务。
   *
   * @param limit 上限，实际限制在 1–200
   */
  List<JobView> listByType(String type, int limit);

  /**
   * 是否存在与主题相关的 pending/running 任务。
   *
   * <p>匹配：entityType=theme 且 entityId=themeId，或 input_json 含该 themeId。
   *
   * @param themeId 主题主键
   * @return 存在进行中任务时为 true
   */
  boolean hasActiveJobsForTheme(String themeId);

  /**
   * 按主题与任务类型取最近一条任务。
   *
   * @param themeId 主题主键
   * @param jobType 任务类型（{@link JobTypes}）
   * @return 最近任务视图；无则 null
   */
  JobView findLatestByThemeAndType(String themeId, String jobType);

  /**
   * 按主题集合与任务类型批量取各自主题最近一条任务（一次 IN 查询）。
   *
   * @param themeIds 主题主键集合
   * @param jobType  任务类型
   * @return themeId → 最近任务；无任务的主题不出现
   */
  java.util.Map<String, JobView> findLatestByThemesAndType(
      java.util.Collection<String> themeIds, String jobType);

  /** 取消非终态任务；已终态则原样返回。 */
  JobView cancel(String jobId);

  /**
   * 将 failed 任务重新排队为 pending；非 failed 抛业务异常。
   * <p>副作用：递增 retryCount，清空错误与租约字段。
   */
  JobView retryFailed(String jobId);

  /**
   * 拉取 id 大于 afterId 的事件，供 SSE 增量。
   *
   * @param afterId 客户端已消费的最大事件 id
   */
  List<JobEventView> eventsAfter(String jobId, long afterId);

  /** 追加事件行（不必然更新任务主状态）。 */
  void appendEvent(
      String jobId, String eventType, String step, int progress, String message, Map<String, Object> payload);

  /** 标记成功并发布 {@link JobTerminalEvent}；已终态则忽略。 */
  void succeed(String jobId, Object output);

  /** 标记失败并发布 {@link JobTerminalEvent}；已终态则忽略。 */
  void fail(String jobId, String errorCode, String errorMessage);

  /** 更新步骤/进度并追加 progress 事件。 */
  void updateProgress(String jobId, String step, int progress, String message);

  /**
   * 更新步骤/进度并追加带附加信息的 progress 事件。
   *
   * @param payload 进度元数据（如 total/current/title），可空
   */
  void updateProgress(String jobId, String step, int progress, String message, Map<String, Object> payload);

  /**
   * 认领下一条可执行任务（pending 或租约过期的 running），写入租约。
   *
   * @param leaseSeconds 租约时长
   * @return 认领到的实体，无候选时返回 null
   */
  JobEntity claimNext(int leaseSeconds);

  /** 延长 running 任务的租约。 */
  void renewLease(String jobId, int leaseSeconds);

  /** 任务状态是否已为 cancelled。 */
  boolean isCancelRequested(String jobId);
}
