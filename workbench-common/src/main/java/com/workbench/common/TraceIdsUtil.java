package com.workbench.common;

/** 链路追踪 MDC / 请求头常量。 */
public final class TraceIdsUtil {
  /** MDC 与日志中的键名。 */
  public static final String MDC_KEY = "traceId";
  /** HTTP 请求/响应头。 */
  public static final String HEADER = "X-Trace-Id";

  private TraceIdsUtil() {}
}
