package com.chengwenfang.quality.facade.constant;

/**
 * 课程审核汇总 Map 键（原 result_json）。
 */
public final class CourseAuditSummaryKeys {
  /** 分数。 */
  public static final String SCORE = "score";
  /** 通过项数（读回时按 issues 重建）。 */
  public static final String PASSED = "passed";
  /** 警告数。 */
  public static final String WARNINGS = "warnings";
  /** 失败数。 */
  public static final String FAILED = "failed";
  /** 是否阻塞。 */
  public static final String BLOCKING = "blocking";

  private CourseAuditSummaryKeys() {}
}
