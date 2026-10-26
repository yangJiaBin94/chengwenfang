package com.workbench.crawler.facade.dto.view;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 抓取结果。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FetchResult {
  /** 是否成功。 */
  private boolean ok;
  /** 失败时的错误码（如 http_403、类名）。 */
  private String errorCode;
  /** 失败时的错误信息。 */
  private String errorMessage;
  /** 成功时的条目列表。 */
  private List<FetchedItemView> items;
}
