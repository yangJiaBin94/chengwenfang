package com.workbench.job.application;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.common.BusinessException;
import com.workbench.common.ErrorCodes;
import com.workbench.common.IdsUtil;
import com.workbench.job.domain.entity.JobEntity;
import com.workbench.job.domain.entity.JobEventEntity;
import com.workbench.job.facade.JobFacade;
import com.workbench.job.facade.dto.job.JobPayloads;
import com.workbench.job.facade.dto.view.JobEventView;
import com.workbench.job.facade.dto.view.JobView;
import com.workbench.job.facade.enums.JobStatus;
import com.workbench.job.facade.dto.view.JobTerminalEvent;
import com.workbench.job.facade.constant.JobEntityTypes;
import com.workbench.job.facade.constant.JobTypes;
import com.workbench.job.infrastructure.mapper.JobEventMapper;
import com.workbench.job.infrastructure.mapper.JobMapper;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link JobFacade} 默认实现：任务与事件持久化，终态时发布 {@link JobTerminalEvent}。
 * <p>{@link #claimNext} 使用 SKIP LOCKED 认领，并对租约过期的 running 任务做恢复计数。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobFacadeImpl implements JobFacade {
  /** 事件 message 落库上限（兼容旧库 varchar(1024)；超长截断，避免整单任务失败）。 */
  private static final int EVENT_MESSAGE_MAX_CHARS = 1000;

  /** 任务主表读写。 */
  private final JobMapper jobMapper;
  /** 任务事件表写入与增量查询。 */
  private final JobEventMapper eventMapper;
  /** 终态时发布站内通知等监听事件。 */
  private final ApplicationEventPublisher events;
  /** 实体到对外视图的映射。 */
  private final JobViewMapper viewMapper;
  /** 租约回收等指标。 */
  private final JobMetrics metrics;

  /**
   * 返回系统支持的任务类型集合。
   *
   * @return {@link JobTypes#ALL} 中的全部类型码
   */
  @Override
  public Set<String> supportedTypes() {
    return JobTypes.ALL;
  }

  /**
   * 创建 pending 任务并写入 queued 事件。
   *
   * @param type       任务类型，须在 {@link JobTypes#ALL} 中
   * @param entityType 关联业务实体类型（可为 null）
   * @param entityId   关联业务实体 id（可为 null）
   * @param input      任务输入；序列化为 input_json，null 视为空对象
   * @return 新创建任务的对外视图
   * @throws BusinessException 类型不在支持集合时抛出
   */
  @Override
  @Transactional
  public JobView create(String type, String entityType, String entityId, Object input) {
    // 先校验类型，避免写入无法被 Worker 处理的脏任务
    if (!JobTypes.ALL.contains(type)) {
      throw new BusinessException(ErrorCodes.UNSUPPORTED_JOB_TYPE, "不支持的任务类型：" + type, 400);
    }
    Map<String, Object> inputMap = JobPayloads.asMap(input);
    // 落库 pending 任务，并追加 queued 事件供 SSE 首包
    JobEntity job = JobEntity.builder()
        .id(IdsUtil.newId())
        .jobType(type)
        .status(JobStatus.PENDING.getValue())
        .entityType(entityType)
        .entityId(entityId)
        .themeId(resolveThemeId(entityType, entityId, input))
        .workId(resolveWorkId(entityType, entityId, input))
        .progress(0)
        .currentStep(JobStatus.QUEUED.getValue())
        .inputJson(JobPayloads.toJson(input))
        .retryCount(0)
        .maxRetries(0)
        .createdAt(LocalDateTime.now())
        .build();
    jobMapper.insert(job);
    // queued 事件携带 skills，供执行页「本次使用的 Skill」展示
    Map<String, Object> queuedPayload = null;
    Object skillSnapshot = inputMap.get("skillSnapshot");
    if (skillSnapshot instanceof List<?> list && !list.isEmpty()) {
      queuedPayload = Map.of("skills", skillSnapshot);
    }
    appendEvent(job.getId(), JobStatus.QUEUED.getValue(), JobStatus.QUEUED.getValue(), 0, "任务已创建", queuedPayload);
    log.info("创建任务 id={} type={} entity={}/{}", job.getId(), type, entityType, entityId);
    return viewMapper.toView(job);
  }

  /**
   * 按 id 查询任务视图。
   *
   * @param jobId 任务主键
   * @return 任务对外视图
   * @throws BusinessException 任务不存在时抛出 404
   */
  @Override
  public JobView get(String jobId) {
    JobView view = viewMapper.toView(require(jobId));
    attachLatestProgress(view, jobId);
    return view;
  }

  /**
   * 将最近一条 progress 事件的说明与 payload 挂到视图上，供前端展示细粒度进度。
   *
   * @param view  任务视图
   * @param jobId 任务 ID
   */
  private void attachLatestProgress(JobView view, String jobId) {
    JobEventEntity latest = eventMapper.selectOne(new LambdaQueryWrapper<JobEventEntity>()
        .eq(JobEventEntity::getJobId, jobId)
        .eq(JobEventEntity::getEventType, "progress")
        .orderByDesc(JobEventEntity::getId)
        .last("LIMIT 1"));
    if (latest == null) {
      return;
    }
    view.setProgressMessage(latest.getMessage());
    view.setProgressMeta(viewMapper.jsonToMap(latest.getPayloadJson()));
  }

  /**
   * 是否存在与主题相关的 pending/running 任务。
   *
   * @param themeId 主题主键
   * @return 存在进行中任务时为 true
   */
  @Override
  public boolean hasActiveJobsForTheme(String themeId) {
    if (StrUtil.isBlank(themeId)) {
      return false;
    }
    // 按 theme_id 列查询，替代原 input_json LIKE
    Long count = jobMapper.selectCount(new LambdaQueryWrapper<JobEntity>()
        .eq(JobEntity::getThemeId, themeId)
        .in(JobEntity::getStatus, JobStatus.PENDING.getValue(), JobStatus.RUNNING.getValue()));
    return count != null && count > 0;
  }

  /**
   * 按主题与任务类型取最近一条任务。
   *
   * @param themeId 主题主键
   * @param jobType 任务类型
   * @return 最近任务视图；无则 null
   */
  @Override
  public JobView findLatestByThemeAndType(String themeId, String jobType) {
    if (StrUtil.isBlank(themeId) || StrUtil.isBlank(jobType)) {
      return null;
    }
    JobEntity entity = jobMapper.selectOne(new LambdaQueryWrapper<JobEntity>()
        .eq(JobEntity::getThemeId, themeId)
        .eq(JobEntity::getJobType, jobType)
        .orderByDesc(JobEntity::getCreatedAt)
        .last("LIMIT 1"));
    return entity == null ? null : viewMapper.toView(entity);
  }

  /**
   * 按主题集合与任务类型批量取各自主题最近一条任务。
   *
   * @param themeIds 主题主键集合
   * @param jobType  任务类型
   * @return themeId → 最近任务
   */
  @Override
  public Map<String, JobView> findLatestByThemesAndType(Collection<String> themeIds, String jobType) {
    Map<String, JobView> latestByTheme = new LinkedHashMap<>();
    if (themeIds == null || themeIds.isEmpty() || StrUtil.isBlank(jobType)) {
      return latestByTheme;
    }
    List<String> ids = themeIds.stream().filter(StrUtil::isNotBlank).distinct().toList();
    if (ids.isEmpty()) {
      return latestByTheme;
    }
    // 按创建时间降序，内存取每个 theme 第一条
    List<JobEntity> rows = jobMapper.selectList(new LambdaQueryWrapper<JobEntity>()
        .in(JobEntity::getThemeId, ids)
        .eq(JobEntity::getJobType, jobType)
        .orderByDesc(JobEntity::getCreatedAt));
    for (JobEntity row : rows) {
      if (row == null || StrUtil.isBlank(row.getThemeId())) {
        continue;
      }
      latestByTheme.putIfAbsent(row.getThemeId(), viewMapper.toView(row));
    }
    return latestByTheme;
  }

  /**
   * 从实体类型或入参解析 theme_id。
   *
   * @param entityType 实体类型
   * @param entityId   实体 ID
   * @param input      任务入参
   * @return 主题 ID；无法解析时为 null
   */
  private static String resolveThemeId(String entityType, String entityId, Object input) {
    if (JobEntityTypes.THEME.equals(entityType) && StrUtil.isNotBlank(entityId)) {
      return entityId;
    }
    Map<String, Object> inputMap = JobPayloads.asMap(input);
    if (inputMap.isEmpty()) {
      return null;
    }
    Object raw = inputMap.get("themeId");
    if (raw == null) {
      return null;
    }
    String themeId = StrUtil.trim(String.valueOf(raw));
    return StrUtil.isBlank(themeId) || "null".equalsIgnoreCase(themeId) ? null : themeId;
  }

  /**
   * 解析作品 ID：课程实体直接用 entityId；否则读入参 workId/courseId。
   *
   * @param entityType 实体类型
   * @param entityId   实体 ID
   * @param input      任务入参
   * @return 作品 ID；无法解析时为 null
   */
  private static String resolveWorkId(String entityType, String entityId, Object input) {
    if (JobEntityTypes.COURSE.equals(entityType) && StrUtil.isNotBlank(entityId)) {
      return entityId;
    }
    Map<String, Object> inputMap = JobPayloads.asMap(input);
    if (inputMap.isEmpty()) {
      return null;
    }
    Object workId = inputMap.get("workId");
    if (workId == null) {
      workId = inputMap.get("courseId");
    }
    if (workId == null) {
      return null;
    }
    String resolved = StrUtil.trim(String.valueOf(workId));
    return StrUtil.isBlank(resolved) || "null".equalsIgnoreCase(resolved) ? null : resolved;
  }

  /**
   * 按类型列出最近创建的任务。
   *
   * @param type  任务类型码
   * @param limit 期望条数上限；实际限制在 1–200，非法或 ≤0 时默认 50
   * @return 按创建时间倒序的任务视图列表
   */
  @Override
  public List<JobView> listByType(String type, int limit) {
    // 夹紧分页上限，防止一次扫出过多行
    int capped = Math.max(1, Math.min(limit <= 0 ? 50 : limit, 200));
    return jobMapper.selectList(new LambdaQueryWrapper<JobEntity>()
            .eq(JobEntity::getJobType, type)
            .orderByDesc(JobEntity::getCreatedAt)
            .last("LIMIT " + capped))
        .stream()
        .map(viewMapper::toView)
        .toList();
  }

  /**
   * 取消非终态任务；已终态则原样返回。
   *
   * @param jobId 任务主键
   * @return 取消后（或已终态）的任务视图
   * @throws BusinessException 任务不存在时抛出 404
   */
  @Override
  @Transactional
  public JobView cancel(String jobId) {
    JobEntity job = require(jobId);
    // 已终态无需再写库，直接回显
    if (isTerminal(job)) {
      return viewMapper.toView(job);
    }
    job.setStatus(JobStatus.CANCELLED.getValue());
    job.setFinishedAt(LocalDateTime.now());
    job.setCurrentStep(JobStatus.CANCELLED.getValue());
    jobMapper.updateById(job);
    appendEvent(jobId, JobStatus.CANCELLED.getValue(), JobStatus.CANCELLED.getValue(), job.getProgress(), "任务已取消", null);
    log.info("取消任务 id={}", jobId);
    return viewMapper.toView(job);
  }

  /**
   * 将 failed 任务重新排队为 pending。
   * <p>副作用：递增 retryCount，清空错误与租约字段。
   *
   * @param jobId 任务主键
   * @return 重新入队后的任务视图
   * @throws BusinessException 任务不存在，或当前状态非 failed
   */
  @Override
  @Transactional
  public JobView retryFailed(String jobId) {
    JobEntity job = require(jobId);
    if (!JobStatus.FAILED.getValue().equals(job.getStatus())) {
      throw new BusinessException(ErrorCodes.INVALID_STATUS, "仅失败任务可重试", 400);
    }
    // 清错误/租约后回到 pending，供 Worker 再次认领
    job.setStatus(JobStatus.PENDING.getValue());
    job.setProgress(0);
    job.setCurrentStep("retry_queued");
    job.setErrorCode(null);
    job.setErrorMessage(null);
    job.setFinishedAt(null);
    job.setStartedAt(null);
    job.setLeaseExpiresAt(null);
    job.setRetryCount(ObjectUtil.defaultIfNull(job.getRetryCount(), 0) + 1);
    jobMapper.updateById(job);
    appendEvent(jobId, JobStatus.QUEUED.getValue(), "retry_queued", 0, "任务已重新排队", null);
    log.info("任务重新入队 id={} retryCount={}", jobId, job.getRetryCount());
    return viewMapper.toView(job);
  }

  /**
   * 拉取 id 大于 afterId 的事件，供 SSE 增量推送。
   *
   * @param jobId   任务主键
   * @param afterId 客户端已消费的最大事件 id；之后的事件才会返回
   * @return 按事件 id 升序的事件视图列表
   * @throws BusinessException 任务不存在时抛出 404
   */
  @Override
  public List<JobEventView> eventsAfter(String jobId, long afterId) {
    // 先确认任务存在，避免对幽灵 jobId 做空查询
    require(jobId);
    return eventMapper.selectList(new LambdaQueryWrapper<JobEventEntity>()
            .eq(JobEventEntity::getJobId, jobId)
            .gt(JobEventEntity::getId, afterId)
            .orderByAsc(JobEventEntity::getId))
        .stream()
        .map(viewMapper::toEventView)
        .toList();
  }

  /**
   * 追加一条任务事件行（不必然更新任务主状态）。
   *
   * @param jobId     任务主键
   * @param eventType 事件类型（如 queued、progress、succeeded）
   * @param step      步骤名；null 记为空串
   * @param progress  进度百分比
   * @param message   人类可读说明；null 记为空串
   * @param payload   附加载荷；null 则不写 payload_json
   */
  @Override
  @Transactional
  public void appendEvent(
      String jobId, String eventType, String step, int progress, String message, Map<String, Object> payload) {
    JobEventEntity event = JobEventEntity.builder()
        .jobId(jobId)
        .eventType(eventType)
        .step(StrUtil.nullToEmpty(step))
        .progress(progress)
        .message(truncateEventMessage(message))
        .payloadJson(payload == null ? null : JobPayloads.toJson(payload))
        .createdAt(LocalDateTime.now())
        .build();
    eventMapper.insert(event);
  }

  /** 截断事件说明，防止超过 job_events.message 列宽导致任务失败。 */
  static String truncateEventMessage(String message) {
    String text = StrUtil.nullToEmpty(message);
    if (text.length() <= EVENT_MESSAGE_MAX_CHARS) {
      return text;
    }
    return text.substring(0, EVENT_MESSAGE_MAX_CHARS - 1) + "…";
  }

  /**
   * 标记任务成功并发布 {@link JobTerminalEvent}；已终态则忽略。
   *
   * @param jobId  任务主键
   * @param output 成功输出；null 视为空对象，写入 output_json 并随终态事件发布
   * @throws BusinessException 任务不存在时抛出 404
   */
  @Override
  @Transactional
  public void succeed(String jobId, Object output) {
    JobEntity job = require(jobId);
    // 幂等：已终态不再重复发布事件
    if (isTerminal(job)) {
      return;
    }
    Map<String, Object> safeOutput = JobPayloads.asMap(output);
    job.setStatus(JobStatus.SUCCEEDED.getValue());
    job.setProgress(100);
    job.setCurrentStep("done");
    job.setOutputJson(JobPayloads.toJson(output));
    job.setFinishedAt(LocalDateTime.now());
    job.setLeaseExpiresAt(null);
    jobMapper.updateById(job);
    appendEvent(jobId, JobStatus.SUCCEEDED.getValue(), "done", 100, "任务已成功", safeOutput);
    // 通知侧监听终态事件创建站内通知
    events.publishEvent(JobTerminalEvent.builder()
        .jobId(job.getId())
        .type(job.getJobType())
        .entityType(job.getEntityType())
        .entityId(job.getEntityId())
        .status(JobStatus.SUCCEEDED.getValue())
        .output(safeOutput)
        .build());
    log.info("任务成功 id={} type={}", jobId, job.getJobType());
  }

  /**
   * 标记任务失败并发布 {@link JobTerminalEvent}；已终态则忽略。
   *
   * @param jobId        任务主键
   * @param errorCode    错误码，写入主表与事件 payload
   * @param errorMessage 错误说明，写入主表并作为事件 message
   * @throws BusinessException 任务不存在时抛出 404
   */
  @Override
  @Transactional
  public void fail(String jobId, String errorCode, String errorMessage) {
    JobEntity job = require(jobId);
    // 幂等：已终态不再重复发布事件
    if (isTerminal(job)) {
      return;
    }
    job.setStatus(JobStatus.FAILED.getValue());
    job.setErrorCode(errorCode);
    job.setErrorMessage(errorMessage);
    job.setFinishedAt(LocalDateTime.now());
    job.setLeaseExpiresAt(null);
    jobMapper.updateById(job);
    appendEvent(jobId, JobStatus.FAILED.getValue(), job.getCurrentStep(), job.getProgress(), errorMessage,
        Map.of("errorCode", StrUtil.nullToEmpty(errorCode)));
    events.publishEvent(JobTerminalEvent.builder()
        .jobId(job.getId())
        .type(job.getJobType())
        .entityType(job.getEntityType())
        .entityId(job.getEntityId())
        .status(JobStatus.FAILED.getValue())
        .errorCode(errorCode)
        .errorMessage(errorMessage)
        .output(Map.of())
        .build());
    log.warn("任务失败 id={} code={} msg={}", jobId, errorCode, errorMessage);
  }

  /**
   * 更新任务当前步骤与进度；进度文案相对上一条 progress 有变化时才追加事件。
   *
   * <p>长步骤（如雷达解析）会按秒续租并反复上报同一文案；若不去重，事件日志会被同文案刷屏。
   *
   * @param jobId    任务主键
   * @param step     当前步骤名
   * @param progress 进度百分比
   * @param message  进度说明
   * @throws BusinessException 任务不存在时抛出 404
   */
  @Override
  @Transactional
  public void updateProgress(String jobId, String step, int progress, String message) {
    updateProgress(jobId, step, progress, message, null);
  }

  @Override
  @Transactional
  public void updateProgress(
      String jobId, String step, int progress, String message, Map<String, Object> payload) {
    JobEntity job = require(jobId);
    // 终态任务不再接受进度更新
    if (isTerminal(job)) {
      return;
    }
    job.setCurrentStep(step);
    job.setProgress(progress);
    jobMapper.updateById(job);
    // 步骤/进度/文案均未变时只刷新任务行，不追加重复 progress 事件
    if (isSameAsLatestProgress(jobId, step, progress, message, payload)) {
      return;
    }
    appendEvent(jobId, "progress", step, progress, message, payload);
  }

  /**
   * 判断本轮进度是否与最近一条 progress 事件相同。
   *
   * @param jobId    任务主键
   * @param step     步骤
   * @param progress 进度
   * @param message  文案
   * @param payload  载荷；可空
   * @return 相同时 true
   */
  private boolean isSameAsLatestProgress(
      String jobId, String step, int progress, String message, Map<String, Object> payload) {
    JobEventEntity latest = eventMapper.selectOne(new LambdaQueryWrapper<JobEventEntity>()
        .eq(JobEventEntity::getJobId, jobId)
        .eq(JobEventEntity::getEventType, "progress")
        .orderByDesc(JobEventEntity::getId)
        .last("LIMIT 1"));
    if (latest == null) {
      return false;
    }
    if (!StrUtil.equals(StrUtil.nullToEmpty(step), StrUtil.nullToEmpty(latest.getStep()))) {
      return false;
    }
    if (!ObjectUtil.equal(progress, latest.getProgress())) {
      return false;
    }
    if (!StrUtil.equals(truncateEventMessage(message), StrUtil.nullToEmpty(latest.getMessage()))) {
      return false;
    }
    String nextPayload = payload == null ? null : JobPayloads.toJson(payload);
    return StrUtil.equals(StrUtil.nullToEmpty(nextPayload), StrUtil.nullToEmpty(latest.getPayloadJson()));
  }

  /**
   * 认领下一条可执行任务（pending 或租约过期的 running），并写入租约。
   *
   * @param leaseSeconds 租约时长（秒）
   * @return 认领到的任务实体；无候选或被并发抢先时返回 null
   */
  @Override
  @Transactional
  public JobEntity claimNext(int leaseSeconds) {
    // 1) SKIP LOCKED 选候选；2) 条件更新为 running；3) 若原为 running 则计恢复
    String id = jobMapper.selectClaimCandidateId();
    if (id == null) {
      return null;
    }
    // 认领前快照状态，用于判断是否租约恢复
    JobEntity before = jobMapper.selectById(id);
    boolean recovering = before != null && JobStatus.RUNNING.getValue().equals(before.getStatus());
    // started_at 用 JVM 本地时钟，与 created_at/finished_at 一致（租约仍用 UTC_TIMESTAMP）
    int updated = jobMapper.claimById(id, leaseSeconds, LocalDateTime.now());
    if (updated == 0) {
      // 被并发 Worker 抢先认领
      return null;
    }
    JobEntity claimed = jobMapper.selectById(id);
    if (recovering && claimed != null) {
      // 租约过期重认：递增 retryCount 并写 warning 事件
      claimed.setRetryCount(ObjectUtil.defaultIfNull(claimed.getRetryCount(), 0) + 1);
      jobMapper.updateById(claimed);
      appendEvent(
          id,
          "warning",
          "recovered",
          ObjectUtil.defaultIfNull(claimed.getProgress(), 0),
          "租约过期，任务已重新认领",
          null);
      // 可观测：租约回收计数
      metrics.recordLeaseReclaim();
      claimed = jobMapper.selectById(id);
      log.warn("任务租约已恢复 id={} retryCount={}", id, claimed.getRetryCount());
    }
    return claimed;
  }

  /**
   * 延长 running 任务的租约，防止长任务被其他 Worker 误认领。
   *
   * @param jobId        任务主键
   * @param leaseSeconds 自当前起再续的租约秒数
   */
  @Override
  public void renewLease(String jobId, int leaseSeconds) {
    jobMapper.renewLease(jobId, leaseSeconds);
  }

  /**
   * 判断任务是否已请求取消（状态为 cancelled）。
   *
   * @param jobId 任务主键
   * @return 任务存在且状态为 cancelled 时为 true；不存在则为 false
   */
  @Override
  public boolean isCancelRequested(String jobId) {
    JobEntity job = jobMapper.selectById(jobId);
    return job != null && JobStatus.CANCELLED.getValue().equals(job.getStatus());
  }

  /**
   * 按 id 加载任务实体，缺失则抛 404。
   *
   * @param jobId 任务主键
   * @return 非空任务实体
   * @throws BusinessException 记录不存在时
   */
  private JobEntity require(String jobId) {
    JobEntity job = jobMapper.selectById(jobId);
    if (job == null) {
      throw new BusinessException(ErrorCodes.JOB_NOT_FOUND, "任务不存在：" + jobId, 404);
    }
    return job;
  }

  /**
   * 判断任务是否已进入终态（succeeded / failed / cancelled）。
   *
   * @param job 任务实体
   * @return 状态属于三者之一时为 true
   */
  private static boolean isTerminal(JobEntity job) {
    return Set.of(
            JobStatus.SUCCEEDED.getValue(),
            JobStatus.FAILED.getValue(),
            JobStatus.CANCELLED.getValue())
        .contains(job.getStatus());
  }

}
