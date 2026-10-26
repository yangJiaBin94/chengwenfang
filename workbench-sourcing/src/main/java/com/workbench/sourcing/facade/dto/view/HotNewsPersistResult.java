package com.workbench.sourcing.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 抓取入库统计。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsPersistResult {
  /** 新插入条数 */
  private int inserted;
  /** 去重条数 */
  private int deduped;
}
