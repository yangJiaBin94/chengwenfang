package com.workbench.job.facade.dto.job;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@link com.workbench.job.facade.constant.JobTypes#HOT_NEWS_VIDEO_PARSE} 出参。
 * <p>字段顺序：ok → parsed → failed → total（total 可空省略）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsVideoParseJobOutput {
  /** 是否成功。 */
  private Boolean ok;
  /** 成功条数。 */
  private Integer parsed;
  /** 失败条数。 */
  private Integer failed;
  /** 总条数；空批无此字段。 */
  private Integer total;
}
