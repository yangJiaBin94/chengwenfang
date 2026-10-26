package com.workbench.ai.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 连通性探测结果。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionTestResult {
  /** 是否成功。 */
  private boolean ok;
  /** 探测的 kind：text / image。 */
  private String kind;
  /** 文本侧探测预览。 */
  private String preview;
  /** 图像侧真实生成结果。 */
  private ImageGenerateResult details;
}
