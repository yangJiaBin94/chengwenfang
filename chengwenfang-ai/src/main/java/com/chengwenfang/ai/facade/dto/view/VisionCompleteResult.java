package com.chengwenfang.ai.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 多模态看图补全结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisionCompleteResult {
  /** 模型输出文本。 */
  private String text;
  /** 实际使用的模型名。 */
  private String model;
  /** 实际使用的协议。 */
  private String protocol;
}
