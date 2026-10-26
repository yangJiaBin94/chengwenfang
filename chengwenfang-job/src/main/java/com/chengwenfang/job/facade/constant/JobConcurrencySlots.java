package com.chengwenfang.job.facade.constant;

import com.chengwenfang.job.facade.enums.JobConcurrencyKind;
import java.util.Set;

/**
 * 将 {@link JobTypes} 映射到 {@link JobConcurrencyKind}，供 Worker 选择分类型信号量。
 */
public final class JobConcurrencySlots {
  /** 走采集/CLI 槽的任务类型。 */
  private static final Set<String> FETCH_TYPES = Set.of(
      JobTypes.HOT_NEWS_FETCH,
      JobTypes.HOT_NEWS_TOPIC_RESEARCH,
      JobTypes.HOT_NEWS_VIDEO_PARSE,
      JobTypes.HOT_NEWS_SOURCE_VIDEO_PARSE);

  private JobConcurrencySlots() {}

  /**
   * 按任务类型解析并发槽种类；未识别类型回退为文本槽。
   *
   * @param jobType 任务 type 字段
   * @return 对应槽种类，永不返回 null
   */
  public static JobConcurrencyKind kindOf(String jobType) {
    if (JobTypes.IMAGE_GENERATION.equals(jobType)) {
      return JobConcurrencyKind.IMAGE;
    }
    if (jobType != null && FETCH_TYPES.contains(jobType)) {
      return JobConcurrencyKind.FETCH;
    }
    return JobConcurrencyKind.TEXT;
  }
}
