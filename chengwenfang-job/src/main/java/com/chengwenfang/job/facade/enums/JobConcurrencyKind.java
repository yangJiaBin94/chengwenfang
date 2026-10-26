package com.chengwenfang.job.facade.enums;

/**
 * Job Worker 分类型并发槽位种类。
 * <p>与 {@code chengwenfang.worker-*-concurrency} 对应；全局上限仍由 {@code worker-concurrency} 约束。
 */
public enum JobConcurrencyKind {
  /** 文本类：成文、审核、修复、课程草稿等。 */
  TEXT("text"),
  /** 配图类：独立出图任务。 */
  IMAGE("image"),
  /** 采集类：雷达拉取、调研、视频解析等偏 I/O/CLI 任务。 */
  FETCH("fetch");

  /** 指标/日志用短名。 */
  private final String value;

  /**
   * @param value 对外短名
   */
  JobConcurrencyKind(String value) {
    this.value = value;
  }

  /**
   * @return 短名（text / image / fetch）
   */
  public String getValue() {
    return value;
  }
}
