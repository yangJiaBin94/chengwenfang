package com.workbench.app.adapter.worker;

import com.workbench.job.domain.entity.JobEntity;
import com.workbench.job.facade.JobExecutionContext;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 仅测试 profile 加载：拦截 article_repair 等任务以便 IT 可控。 */
@Profile("test")
@Component
public class JobTestGate implements JobExecutionHook {
  /** 是否启用阻塞闸门。 */
  private volatile boolean blockEnabled;
  /** 等待 parties 个 Worker 进入拦截点。 */
  private volatile CountDownLatch entered = new CountDownLatch(1);
  /** 放行信号。 */
  private volatile CountDownLatch release = new CountDownLatch(1);
  /** 当前在闸门内的并发数。 */
  private final AtomicInteger inFlight = new AtomicInteger();
  /** 观测到的最大并发。 */
  private final AtomicInteger maxInFlight = new AtomicInteger();

  /**
   * 启用阻塞：parties 个 Worker 进入拦截点后测试方可继续断言。
   *
   * @param parties 期望进入拦截点的 Worker 数
   */
  public void armBlock(int parties) {
    blockEnabled = true;
    inFlight.set(0);
    maxInFlight.set(0);
    entered = new CountDownLatch(parties);
    release = new CountDownLatch(1);
  }

  /** 关闭阻塞并释放所有等待中的 Worker。 */
  public void disarm() {
    blockEnabled = false;
    release.countDown();
  }

  /**
   * 等待指定数量的 Worker 已进入拦截点。
   *
   * @param timeout 超时时长
   * @param unit    时间单位
   * @return true 表示在超时前已凑齐 parties
   * @throws InterruptedException 等待被中断
   */
  public boolean awaitEntered(long timeout, TimeUnit unit) throws InterruptedException {
    return entered.await(timeout, unit);
  }

  /** 放行全部被阻塞的 Worker。 */
  public void releaseAll() {
    release.countDown();
  }

  /**
   * 返回测试期间观测到的最大并发 Worker 数。
   *
   * @return 最大 in-flight 数
   */
  public int maxInFlight() {
    return maxInFlight.get();
  }

  /**
   * article_repair 执行前钩子：阻塞直至 {@link #releaseAll()}，并回报是否已取消。
   *
   * @param job     任务实体
   * @param context 执行上下文（用于检测取消）
   * @return true 表示任务已取消，Handler 应中止
   * @throws Exception 释放超时等
   */
  @Override
  public boolean beforeArticleRepair(JobEntity job, JobExecutionContext context) throws Exception {
    if (!blockEnabled) {
      return false;
    }
    // 记录并发峰值，并通知测试侧「已进入」
    int n = inFlight.incrementAndGet();
    maxInFlight.accumulateAndGet(n, Math::max);
    entered.countDown();
    if (!release.await(15, TimeUnit.SECONDS)) {
      throw new IllegalStateException("JobTestGate 释放超时 jobId=" + job.getId());
    }
    inFlight.decrementAndGet();
    return context.isCancelled(job.getId());
  }
}
