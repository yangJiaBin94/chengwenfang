package com.workbench.job.application;

import com.workbench.infra.config.WorkbenchProperties;
import com.workbench.job.domain.entity.JobEntity;
import com.workbench.infra.support.JobExecutionScope;
import com.workbench.job.facade.JobExecutionContext;
import com.workbench.job.facade.JobFacade;
import com.workbench.job.facade.JobHandler;
import com.workbench.job.facade.constant.JobConcurrencySlots;
import com.workbench.job.facade.dto.job.JobPayloads;
import com.workbench.job.facade.dto.view.JobView;
import com.workbench.job.facade.enums.JobConcurrencyKind;
import com.workbench.job.facade.enums.JobStatus;
import jakarta.annotation.PreDestroy;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 进程内 Job Worker：循环认领任务，按类型分发给 {@link JobHandler}，并在结束时 succeed/fail。
 * <p>受 {@code workbench.worker-enabled} 控制。并发：全局槽 {@link WorkbenchProperties#getWorkerConcurrency()}
 * 与分类型槽（text / image / fetch）同时生效，防止重任务饿死短任务。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "workbench", name = "worker-enabled", havingValue = "true", matchIfMissing = true)
public class JobWorkerRunner {
  /** 任务门面：认领、进度、终态与取消查询。 */
  private final JobFacade jobs;
  /** 运行时配置：并发度、租约与轮询间隔。 */
  private final WorkbenchProperties properties;
  /** 按任务类型索引的处理器表。 */
  private final Map<String, JobHandler> handlers;
  /** 实际执行 Handler 的固定线程池（大小=全局并发）。 */
  private final ExecutorService pool;
  /** 全局并发槽：同时在跑任务总数上限。 */
  private final Semaphore overallSlots;
  /** 分类型并发槽。 */
  private final Map<JobConcurrencyKind, Semaphore> typedSlots;
  /** Job 指标（耗时等）。 */
  private final JobMetrics metrics;
  /** 认领循环是否继续运行。 */
  private final AtomicBoolean running = new AtomicBoolean(true);
  /** 专用认领循环线程。 */
  private final Thread loopThread;
  /** 已提交尚未结束的任务 id，防止同一任务被重复派发。 */
  private final Map<String, Boolean> inFlight = new ConcurrentHashMap<>();

  /**
   * 组装处理器映射、全局/分类型信号量与线程池，并启动认领循环线程。
   *
   * @param jobs         任务门面
   * @param properties   Workbench 配置（并发、租约、轮询）
   * @param handlerList  Spring 注入的全部 {@link JobHandler} 实现
   * @param metrics      任务耗时等指标
   */
  public JobWorkerRunner(
      JobFacade jobs,
      WorkbenchProperties properties,
      List<JobHandler> handlerList,
      JobMetrics metrics) {
    this.jobs = jobs;
    this.properties = properties;
    this.metrics = metrics;
    // 同 type 多个实现时以后者覆盖，便于调试覆盖
    this.handlers = handlerList.stream()
        .collect(Collectors.toMap(JobHandler::type, Function.identity(), (existing, replacement) -> replacement));
    int overall = Math.max(1, properties.getWorkerConcurrency());
    this.pool = Executors.newFixedThreadPool(overall, runnable -> {
      Thread worker = new Thread(runnable, "workbench-job-worker");
      worker.setDaemon(true);
      return worker;
    });
    this.overallSlots = new Semaphore(overall);
    this.typedSlots = buildTypedSlots(properties, overall);
    this.loopThread = new Thread(this::loop, "workbench-job-claim-loop");
    this.loopThread.setDaemon(true);
    this.loopThread.start();
    log.info(
        "任务 Worker 已启动 overall={} text={} image={} fetch={} handlers={}",
        overall,
        typedSlots.get(JobConcurrencyKind.TEXT).availablePermits(),
        typedSlots.get(JobConcurrencyKind.IMAGE).availablePermits(),
        typedSlots.get(JobConcurrencyKind.FETCH).availablePermits(),
        handlers.keySet());
  }

  /**
   * 按配置构建分类型信号量；类型并发未配或 &lt;=0 时回退为全局上限。
   *
   * @param properties 配置
   * @param overall    全局并发（回退值）
   * @return 种类 → 信号量
   */
  private static Map<JobConcurrencyKind, Semaphore> buildTypedSlots(
      WorkbenchProperties properties, int overall) {
    Map<JobConcurrencyKind, Semaphore> slots = new EnumMap<>(JobConcurrencyKind.class);
    slots.put(JobConcurrencyKind.TEXT, new Semaphore(resolveTypedLimit(properties.getWorkerTextConcurrency(), overall)));
    slots.put(JobConcurrencyKind.IMAGE, new Semaphore(resolveTypedLimit(properties.getWorkerImageConcurrency(), overall)));
    slots.put(JobConcurrencyKind.FETCH, new Semaphore(resolveTypedLimit(properties.getWorkerFetchConcurrency(), overall)));
    return slots;
  }

  /**
   * 解析分类型槽容量：&lt;=0 时用全局上限兜底，且至少为 1。
   *
   * @param configured 配置值
   * @param overall    全局上限
   * @return 实际permits
   */
  private static int resolveTypedLimit(int configured, int overall) {
    if (configured <= 0) {
      return overall;
    }
    return Math.max(1, configured);
  }

  /**
   * 认领循环：占全局槽 → claimNext → 占类型槽 → 提交线程池；无任务时释放并休眠。
   */
  private void loop() {
    while (running.get()) {
      boolean overallHeld = false;
      try {
        // 无空闲全局槽则短等，避免空转占满 CPU
        if (!overallSlots.tryAcquire(200, TimeUnit.MILLISECONDS)) {
          continue;
        }
        overallHeld = true;
        JobEntity job = jobs.claimNext(properties.getWorkerLeaseSeconds());
        if (job == null) {
          // 无候选：还全局槽并按配置轮询间隔休眠
          overallSlots.release();
          overallHeld = false;
          sleepQuiet(properties.getWorkerPollSeconds());
          continue;
        }
        // 同 id 已在飞则丢弃本次认领结果（全局槽已占，需释放）
        if (inFlight.putIfAbsent(job.getId(), Boolean.TRUE) != null) {
          overallSlots.release();
          overallHeld = false;
          continue;
        }
        JobConcurrencyKind kind = JobConcurrencySlots.kindOf(job.getJobType());
        Semaphore typedSlot = typedSlots.get(kind);
        // 类型槽满时阻塞等待，避免超并发跑 Whisper/出图；持有全局槽期间不再认领新任务
        try {
          typedSlot.acquire();
        } catch (InterruptedException interrupted) {
          // 已认领但未入池：标记失败，避免任务挂在 running
          inFlight.remove(job.getId());
          overallSlots.release();
          overallHeld = false;
          failClaimedJobQuietly(job.getId(), "worker_interrupted", "Worker 在等待类型槽时被中断");
          Thread.currentThread().interrupt();
          break;
        }
        overallHeld = false; // 此后由 runJob finally 归还全局槽
        JobExecutionContext context = new JobExecutionContext(jobs, properties);
        try {
          pool.execute(() -> runJob(job, context, typedSlot));
        } catch (RuntimeException submitFailed) {
          // 线程池拒绝等：归还双槽并失败任务，避免 permit/inFlight 泄漏
          inFlight.remove(job.getId());
          typedSlot.release();
          overallSlots.release();
          failClaimedJobQuietly(
              job.getId(),
              "worker_submit_rejected",
              submitFailed.getMessage() == null ? submitFailed.toString() : submitFailed.getMessage());
          log.warn("任务入池失败 id={} err={}", job.getId(), submitFailed.toString());
        }
      } catch (InterruptedException ignored) {
        if (overallHeld) {
          overallSlots.release();
        }
        Thread.currentThread().interrupt();
        break;
      } catch (Exception exception) {
        if (overallHeld) {
          overallSlots.release();
        }
        log.warn("认领循环异常：{}", exception.toString());
        sleepQuiet(1.0);
      }
    }
  }

  /**
   * 在工作线程中执行单个任务：调 Handler，未终态则 succeed，异常则 fail；最后还类型槽与全局槽。
   *
   * @param job       已认领的任务实体
   * @param context   执行上下文（进度、租约续期、完成输出）
   * @param typedSlot 本任务占用的分类型信号量
   */
  private void runJob(JobEntity job, JobExecutionContext context, Semaphore typedSlot) {
    long startedAtMs = System.currentTimeMillis();
    String outcome = "failed";
    // 绑定当前任务，供 AI model_calls.job_id 等跨层写入
    JobExecutionScope.setCurrentJobId(job.getId());
    try {
      log.info("任务开始 id={} type={} entity={}/{}",
          job.getId(), job.getJobType(), job.getEntityType(), job.getEntityId());
      jobs.appendEvent(job.getId(), "started", "started", 0, "任务已开始", null);
      JobHandler handler = handlers.get(job.getJobType());
      if (handler == null) {
        // 类型无处理器：直接失败，避免任务挂死在 running
        jobs.fail(job.getId(), "no_handler", "无对应处理器，类型：" + job.getJobType());
        outcome = "failed";
        return;
      }
      handler.execute(job, context);
      // 执行中被取消：Handler 可能已提前结束，此处不再 succeed
      if (jobs.isCancelRequested(job.getId())) {
        log.info("任务执行中被取消 id={}", job.getId());
        outcome = "cancelled";
        return;
      }
      JobView view = jobs.get(job.getId());
      // Handler 未自行终态时，由 Runner 统一 succeed
      if (JobStatus.RUNNING.getValue().equals(view.getStatus())
          || JobStatus.PENDING.getValue().equals(view.getStatus())) {
        Object output = context.completionOutput() == null ? JobPayloads.okTrue() : context.completionOutput();
        jobs.succeed(job.getId(), output);
        outcome = "succeeded";
      } else {
        outcome = view.getStatus() == null ? "unknown" : view.getStatus();
      }
    } catch (Exception exception) {
      log.error("任务失败 id={} err={}", job.getId(), exception.toString());
      outcome = "failed";
      try {
        // 已取消则不再覆盖为 failed
        if (!jobs.isCancelRequested(job.getId())) {
          jobs.fail(job.getId(), "execution_error", exception.getMessage() == null ? exception.toString() : exception.getMessage());
        } else {
          outcome = "cancelled";
        }
      } catch (Exception ignored) {
        // ignore secondary failure
      }
    } finally {
      JobExecutionScope.clear();
      // 无论成败都记耗时、清 inFlight 并还类型槽与全局槽
      metrics.recordDuration(job.getJobType(), outcome, System.currentTimeMillis() - startedAtMs);
      inFlight.remove(job.getId());
      typedSlot.release();
      overallSlots.release();
    }
  }

  /**
   * 可中断的安静休眠；被中断时恢复中断标志。
   *
   * @param seconds 休眠秒数（可小数）
   */
  private void sleepQuiet(double seconds) {
    try {
      Thread.sleep((long) (seconds * 1000));
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * 已认领但未能进入执行时，安静标失败（忽略二次异常）。
   *
   * @param jobId   任务 id
   * @param code    错误码
   * @param message 说明
   */
  private void failClaimedJobQuietly(String jobId, String code, String message) {
    try {
      jobs.fail(jobId, code, message);
    } catch (Exception ignored) {
      // 认领失败路径不阻断 Worker 主循环
    }
  }

  /**
   * 停止认领循环并强制关闭工作线程池。
   * <p>由容器销毁时通过 {@link PreDestroy} 调用。
   */
  @PreDestroy
  public void shutdown() {
    running.set(false);
    loopThread.interrupt();
    pool.shutdownNow();
  }
}
