package com.chengwenfang.ai.facade.dto.view;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 多模态补全结果。
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
  /** 联网引用（URL 真相源）；未开启联网时为空列表。 */
  @Builder.Default
  private List<WebSearchCitation> citations = new ArrayList<>();
}
