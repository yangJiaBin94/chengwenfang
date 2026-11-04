package com.chengwenfang.crawler.facade.dto.view;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 网页分段截图 CLI 结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageScreenshotResult {
  /** 是否成功。 */
  private boolean ok;
  /** PNG 绝对路径列表。 */
  @Builder.Default
  private List<String> paths = new ArrayList<>();
  /** 失败说明。 */
  private String error;
}
