package com.chengwenfang.job.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.job.domain.entity.JobEntity;
import com.chengwenfang.job.facade.enums.JobStatus;
import com.chengwenfang.job.infrastructure.mapper.JobMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Job 可观测性：pending/running 存量、租约回收次数、按类型执行耗时。
 * <p>无 {@link MeterRegistry} 时全部为空操作，不影响 Worker 主路径。
 */
@Component
public class JobMetrics {
  /** Micrometer 注册表；开发/测试未装配时可为空。 */
  private final MeterRegistry registry;
  /** 按状态查库，供 Gauge 采样。 */
  private final JobMapper jobMapper;
  /** 租约过期重认领计数；registry 为空时为 null。 */
  private final Counter leaseReclaimCounter;

  /**
   * 注册 pending/running Gauge 与租约回收 Counter。
   *
   * @param registries Spring 可选的 MeterRegistry（Actuator 提供）
   * @param jobMapper  任务表访问
   */
  public JobMetrics(ObjectProvider<MeterRegistry> registries, JobMapper jobMapper) {
    this.registry = registries.getIfAvailable();
    this.jobMapper = jobMapper;
    if (this.registry == null) {
      this.leaseReclaimCounter = null;
      return;
    }
    // 存量：每次 scrape 查库，单机工作台可接受
    Gauge.builder("chengwenfang.jobs", this, metrics -> metrics.countByStatus(JobStatus.PENDING))
        .tag("status", JobStatus.PENDING.getValue())
        .description("当前 pending 任务数")
        .register(this.registry);
    Gauge.builder("chengwenfang.jobs", this, metrics -> metrics.countByStatus(JobStatus.RUNNING))
        .tag("status", JobStatus.RUNNING.getValue())
        .description("当前 running 任务数")
        .register(this.registry);
    this.leaseReclaimCounter = Counter.builder("chengwenfang.jobs.lease.reclaim")
        .description("租约过期后重新认领次数")
        .register(this.registry);
  }

  /**
   * 记录一次租约过期恢复认领。
   */
  public void recordLeaseReclaim() {
    if (leaseReclaimCounter != null) {
      leaseReclaimCounter.increment();
    }
  }

  /**
   * 记录任务执行耗时（按 job type 打标签）。
   *
   * @param jobType    任务类型
   * @param outcome    结果标签（succeeded / failed / cancelled 等）
   * @param durationMs 耗时毫秒
   */
  public void recordDuration(String jobType, String outcome, long durationMs) {
    if (registry == null) {
      return;
    }
    Timer.builder("chengwenfang.jobs.duration")
        .description("任务执行耗时")
        .tag("type", jobType == null ? "unknown" : jobType)
        .tag("outcome", outcome == null ? "unknown" : outcome)
        .register(registry)
        .record(Math.max(0L, durationMs), TimeUnit.MILLISECONDS);
  }

  /**
   * 按状态统计任务行数。
   *
   * @param status 任务状态
   * @return 行数；异常时返回 0，避免 Gauge 拖垮 scrape
   */
  private double countByStatus(JobStatus status) {
    try {
      Long count = jobMapper.selectCount(new LambdaQueryWrapper<JobEntity>()
          .eq(JobEntity::getStatus, status.getValue()));
      return count == null ? 0D : count.doubleValue();
    } catch (Exception ignored) {
      return 0D;
    }
  }
}
