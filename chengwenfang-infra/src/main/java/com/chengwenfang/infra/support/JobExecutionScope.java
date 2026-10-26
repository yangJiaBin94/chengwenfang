package com.chengwenfang.infra.support;

import cn.hutool.core.util.StrUtil;

/**
 * 任务执行线程上下文：Worker 入口设置当前 jobId，供 AI 审计等跨层写入维度列。
 *
 * <p>同步调用（无 Worker）时 {@link #currentJobId()} 返回 null。
 */
public final class JobExecutionScope {
  /** 当前线程绑定的任务主键。 */
  private static final ThreadLocal<String> CURRENT_JOB_ID = new ThreadLocal<>();

  private JobExecutionScope() {}

  /**
   * 绑定当前任务 ID（空白则清除）。
   *
   * @param jobId 任务主键
   */
  public static void setCurrentJobId(String jobId) {
    if (StrUtil.isBlank(jobId)) {
      CURRENT_JOB_ID.remove();
      return;
    }
    CURRENT_JOB_ID.set(jobId);
  }

  /** 清除当前线程绑定。 */
  public static void clear() {
    CURRENT_JOB_ID.remove();
  }

  /**
   * 当前线程任务 ID。
   *
   * @return 任务主键；未绑定时为 null
   */
  public static String currentJobId() {
    return CURRENT_JOB_ID.get();
  }
}
