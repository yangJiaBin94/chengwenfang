package com.chengwenfang.sourcing.application.videoparse;

/**
 * 雷达信源单条视频解析处理结果。
 */
public enum SourceVideoParseItemOutcome {
  /** 解析通过并已挂接选题。 */
  READY,
  /** 解析失败或业务弃用。 */
  DISCARDED,
  /** 条目已不存在等跳过。 */
  SKIPPED
}
