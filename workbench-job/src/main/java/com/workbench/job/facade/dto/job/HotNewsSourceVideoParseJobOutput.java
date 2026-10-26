package com.workbench.job.facade.dto.job;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@link com.workbench.job.facade.constant.JobTypes#HOT_NEWS_SOURCE_VIDEO_PARSE} 出参。
 * <p>合并两种变体：ok/parsed/discarded 与 ok/ready/discarded/total；未用字段为 null 省略。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsSourceVideoParseJobOutput {
  /** 是否成功。 */
  private Boolean ok;
  /** 解析成功条数（未启用/空批路径）。 */
  private Integer parsed;
  /** 就绪条数（正常处理路径）。 */
  private Integer ready;
  /** 弃用条数。 */
  private Integer discarded;
  /** 本批总数。 */
  private Integer total;
}
