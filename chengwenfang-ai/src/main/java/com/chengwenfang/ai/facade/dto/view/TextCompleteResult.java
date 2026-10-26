package com.chengwenfang.ai.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 文本补全结果。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TextCompleteResult {
  /** 生成文本。 */
  private String text;
  /** 使用的模型。 */
  private String model;
  /** 协议。 */
  private String protocol;
}
